use futures::StreamExt;
use futures::FutureExt;
use tokio::sync::mpsc;
use std::collections::HashMap;
use std::sync::{Arc, atomic::{AtomicUsize, Ordering}};
use std::sync::RwLock;
use tokio_stream::wrappers::UnboundedReceiverStream;
use tracing::{debug, error, info};
use warp::{filters::ws::{Message, WebSocket}, reject::Rejection, reply::Reply};

use crate::shopping_list::ShoppingItem;
use crate::shopping_list::ShoppingListContent;

pub struct User {
    subscribed_list_name: String,
    sender: mpsc::UnboundedSender<Result<Message, warp::Error>>
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
            let _ = user.sender.send(Ok(Message::text(&reply)));
            debug!("Sent state update for list {}  to subscriber {}", user.subscribed_list_name, id);
        }
    }
}

// Handler for POST item endpoint
pub async fn update_shopping_item(updated_item: ShoppingItem, shopping_list: Arc<RwLock<ShoppingListContent>>, users: Users, query: HashMap<String, String>) -> Result<impl Reply, Rejection> {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());

    if let Some(list) = shopping_list.write().unwrap().get_mut(&list_name) {
        info!("Updating item {:?} in list {}.", updated_item, list_name);
        list.insert(updated_item.id, updated_item);
    
    }
    update(shopping_list.clone(), users);
    Ok(warp::reply())
}

// Handler for DELETE checked endpoint
pub async fn delete_checked(shopping_list: Arc<RwLock<ShoppingListContent>>, users: Users, query: HashMap<String, String>) -> Result<impl Reply, Rejection> {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());

    if let Some(list) = shopping_list.write().unwrap().get_mut(&list_name) {
        let old_count = list.len();
        list.retain(|_, value| !value.checked);
        let new_count = list.len();
        info!("Removed {} checked items.", old_count - new_count);

    }
    update(shopping_list.clone(), users);
    Ok(warp::reply())
}

// Handler for DELETE all endpoint
pub async fn delete_all(shopping_list: Arc<RwLock<ShoppingListContent>>, users: Users, query: HashMap<String, String>) -> Result<impl Reply, Rejection> {
    let list_name = query.get("list_name").cloned().unwrap_or_else(|| "junkyard".into());

    if let Some(list) = shopping_list.write().unwrap().get_mut(&list_name) {
        list.clear();
        info!("Removed all items.");

    }
    update(shopping_list.clone(), users);
    Ok(warp::reply())
}

// Websocket handler
pub async fn user_connected(ws: WebSocket, shopping_list: Arc<RwLock<ShoppingListContent>>, users: Users, list_name: String) {
    // Use a counter to assign a new unique ID for this user.
    let my_id = NEXT_USER_ID.fetch_add(1, Ordering::Relaxed);

    info!("Subscriber connected: {}", my_id);

    // Split the socket into a sender and receive of messages.
    let (user_ws_tx, mut user_ws_rx) = ws.split();

    // Use an unbounded channel to handle buffering and flushing of messages
    // to the websocket...
    let (tx, rx) = mpsc::unbounded_channel();
    let rx = UnboundedReceiverStream::new(rx);
    tokio::task::spawn(rx.forward(user_ws_tx).map(|result| {
        if let Err(e) = result {
            error!("websocket send error: {}", e);
        }
    }));

    // Save the sender in our list of connected users.
    users.write().unwrap().insert(my_id, User { subscribed_list_name: list_name.clone(), sender: tx });
    shopping_list.write().unwrap()
        .entry(list_name.clone()).or_insert(HashMap::new());

    update(shopping_list.clone(), users.clone());

    while let Some(result) = user_ws_rx.next().await {
        let _ = match result {
            Ok(msg) => msg,
            Err(e) => {
                error!("websocket error(uid={}): {}", my_id, e);
                break;
            }
        };
    }

    user_disconnected(my_id, users.clone()).await;
}

pub async fn user_disconnected(my_id: usize, users: Users) {
    info!("subscriber disconnected: {}", my_id);
    users.write().unwrap().remove(&my_id);
}