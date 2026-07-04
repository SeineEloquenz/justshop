use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use futures::{SinkExt, StreamExt};
use std::collections::HashMap;
use std::sync::RwLock;
use std::sync::{atomic::{AtomicUsize, Ordering}, Arc};
use tokio::sync::mpsc;
use tracing::{debug, error, info};

use crate::shopping_list::ShoppingItem;
use crate::shopping_list::ShoppingListContent;

#[derive(Clone)]
pub struct AppState {
    pub shopping_list: Arc<RwLock<ShoppingListContent>>,
    pub users: Users,
}

pub struct User {
    subscribed_list_name: String,
    sender: mpsc::UnboundedSender<Message>,
}

static NEXT_USER_ID: AtomicUsize = AtomicUsize::new(1);
pub type Users = Arc<RwLock<HashMap<usize, User>>>;

pub fn update(shopping_list: Arc<RwLock<ShoppingListContent>>, users: Users) {
    let users = users.read().unwrap();
    info!("Updating {} subscribers", users.len());

    for (id, user) in users.iter() {
        debug!("User {} has list {}", id, user.subscribed_list_name);
        if let Some(list) = shopping_list.read().unwrap().get(&user.subscribed_list_name) {
            let reply = serde_json::to_string_pretty(&list).unwrap();
            let _ = user.sender.send(Message::Text(reply.into()));
            debug!("Sent state update for list {}  to subscriber {}", user.subscribed_list_name, id);
        }
    }
}

// Handler for POST item endpoint
pub async fn update_shopping_item(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
    Json(updated_item): Json<ShoppingItem>,
) -> StatusCode {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());

    if let Some(list) = state.shopping_list.write().unwrap().get_mut(&list_name) {
        info!("Updating item {:?} in list {}.", updated_item, list_name);
        list.insert(updated_item.id, updated_item);
    }
    update(state.shopping_list.clone(), state.users);
    StatusCode::OK
}

// Handler for DELETE checked endpoint
pub async fn delete_checked(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> StatusCode {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());

    if let Some(list) = state.shopping_list.write().unwrap().get_mut(&list_name) {
        let old_count = list.len();
        list.retain(|_, value| !value.checked);
        let new_count = list.len();
        info!("Removed {} checked items.", old_count - new_count);
    }
    update(state.shopping_list.clone(), state.users);
    StatusCode::OK
}

// Handler for DELETE all endpoint
pub async fn delete_all(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> StatusCode {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());

    if let Some(list) = state.shopping_list.write().unwrap().get_mut(&list_name) {
        list.clear();
        info!("Removed all items.");
    }
    update(state.shopping_list.clone(), state.users);
    StatusCode::OK
}

// Websocket upgrade handler
pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());
    ws.on_upgrade(move |socket| handle_socket(socket, state.shopping_list, state.users, list_name))
}

// Per-connection websocket handler
pub async fn handle_socket(
    socket: WebSocket,
    shopping_list: Arc<RwLock<ShoppingListContent>>,
    users: Users,
    list_name: String,
) {
    // Use a counter to assign a new unique ID for this user.
    let my_id = NEXT_USER_ID.fetch_add(1, Ordering::Relaxed);

    info!("Subscriber connected: {}", my_id);

    // Split the socket into a sender and receiver of messages.
    let (mut sink, mut receiver) = socket.split();

    // Use an unbounded channel to handle buffering and flushing of messages
    // to the websocket...
    let (tx, mut rx) = mpsc::unbounded_channel::<Message>();
    tokio::task::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if let Err(e) = sink.send(msg).await {
                error!("websocket send error: {}", e);
                break;
            }
        }
    });

    // Save the sender in our list of connected users.
    users.write().unwrap().insert(my_id, User { subscribed_list_name: list_name.clone(), sender: tx });
    shopping_list.write().unwrap()
        .entry(list_name.clone()).or_insert(HashMap::new());

    update(shopping_list.clone(), users.clone());

    while let Some(result) = receiver.next().await {
        if let Err(e) = result {
            error!("websocket error(uid={}): {}", my_id, e);
            break;
        }
    }

    user_disconnected(my_id, users.clone()).await;
}

pub async fn user_disconnected(my_id: usize, users: Users) {
    info!("subscriber disconnected: {}", my_id);
    users.write().unwrap().remove(&my_id);
}
