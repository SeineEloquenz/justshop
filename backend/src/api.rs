use futures::StreamExt;
use futures::FutureExt;
use serde::{Deserialize, Serialize};
use tokio::sync::mpsc;
use std::collections::HashMap;
use std::sync::{Arc, atomic::{AtomicUsize, Ordering}};
use std::sync::RwLock;
use tokio_stream::wrappers::UnboundedReceiverStream;
use tracing::{debug, error, info};
use uuid::Uuid;
use warp::{filters::ws::{Message, WebSocket}, reject::Rejection, reply::Reply};

use crate::shopping_list::ShoppingItem;

static NEXT_USER_ID: AtomicUsize = AtomicUsize::new(1);
pub type Users = Arc<RwLock<HashMap<usize, mpsc::UnboundedSender<Result<Message, warp::Error>>>>>;

pub fn update(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) {
    let users = users.read().unwrap();
    info!("Updating {} subscribers", users.len());
    let shopping_list = shopping_list.read().unwrap();

    let reply = serde_json::to_string_pretty(&*shopping_list).unwrap();

    for (id, tx) in users.iter() {
        let _ = tx.send(Ok(Message::text(&reply)));
        debug!("Sent state update to subscriber {}", id);
    }
}

// Handler for POST item endpoint
pub async fn update_shopping_item(updated_item: ShoppingItem, shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    info!("Updating item {:?}.", updated_item);
    list.insert(updated_item.id, updated_item);
    drop(list);

    update(shopping_list, users);
    Ok(warp::reply())
}

// Handler for DELETE checked endpoint
pub async fn delete_checked(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    let old_count = list.len();
    list.retain(|_, value| !value.checked);
    let new_count = list.len();
    info!("Removed {} checked items.", old_count - new_count);
    drop(list);

    update(shopping_list, users);
    Ok(warp::reply())
}

// Handler for DELETE all endpoint
pub async fn delete_all(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.clear();
    info!("Removed all items.");
    drop(list);

    update(shopping_list, users);
    Ok(warp::reply())
}

// Websocket handler
pub async fn user_connected(ws: WebSocket, users: Users) {
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
    users.write().unwrap().insert(my_id, tx);

    // Return a `Future` that is basically a state machine managing
    // this specific user's connection.

    // Make an extra clone to give to our disconnection handler...
    let users2 = users.clone();

    while let Some(result) = user_ws_rx.next().await {
        let _ = match result {
            Ok(msg) => msg,
            Err(e) => {
                error!("websocket error(uid={}): {}", my_id, e);
                break;
            }
        };
    }

    user_disconnected(my_id, &users2).await;
}

pub async fn user_disconnected(my_id: usize, users: &Users) {
    info!("subscriber disconnected: {}", my_id);
    users.write().unwrap().remove(&my_id);
}