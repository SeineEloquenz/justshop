use clap::Parser;
use futures::StreamExt;
use futures::FutureExt;
use tokio::{fs, sync::mpsc};
use tokio_stream::wrappers::UnboundedReceiverStream;
use tracing::debug;
use tracing::error;
use std::sync::{atomic::{AtomicUsize, Ordering},Arc, RwLock};
use std::collections::HashMap;
use serde::{Deserialize, Serialize};
use std::path::{Path,PathBuf};
use std::io:: {BufReader,BufWriter};
use std::fs::File;
use tokio::time::Duration;
use tracing::info;
use uuid::Uuid;
use warp::{filters::ws::{Message, WebSocket}, Filter, Rejection, Reply};

#[derive(Parser, Debug, Clone)]
struct Cli {
    #[arg(short, long, default_value = "justshop.json")]
    state_file: PathBuf,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct ShoppingItem {
    id: Uuid,
    content: String,
    checked: bool,
    timestamp: i64,
}

#[derive(Debug, Serialize, Deserialize)]
struct ShoppingList(HashMap<Uuid, ShoppingItem>);

fn update(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) {
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
async fn update_shopping_item(updated_item: ShoppingItem, shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    info!("Updating item {:?}.", updated_item);
    list.insert(updated_item.id, updated_item);
    drop(list);

    update(shopping_list, users);
    Ok(warp::reply())
}

// Handler for DELETE checked endpoint
async fn delete_checked(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) -> Result<impl Reply, Rejection> {
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
async fn delete_all(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>, users: Users) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.clear();
    info!("Removed all items.");
    drop(list);

    update(shopping_list, users);
    Ok(warp::reply())
}

// Websocket handler
async fn user_connected(ws: WebSocket, users: Users) {
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
        let msg = match result {
            Ok(msg) => msg,
            Err(e) => {
                error!("websocket error(uid={}): {}", my_id, e);
                break;
            }
        };
    }

    user_disconnected(my_id, &users2).await;
}

async fn user_disconnected(my_id: usize, users: &Users) {
    info!("subscriber disconnected: {}", my_id);
    users.write().unwrap().remove(&my_id);
}

static NEXT_USER_ID: AtomicUsize = AtomicUsize::new(1);
type Users = Arc<RwLock<HashMap<usize, mpsc::UnboundedSender<Result<Message, warp::Error>>>>>;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().init();

    // Initialize empty shopping list

    let cli = Cli::parse();

    let state_path = cli.state_file;
    let shopping_list = load_state(&state_path).expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));

    let users = Users::default();

    let ws_users = users.clone();
    let websocket = warp::path!("v1" / "ws")
        .and(warp::ws())
        .and(warp::any().map(move || ws_users.clone()))
        .map(|ws: warp::ws::Ws, users| {
            ws.on_upgrade(move |socket| user_connected(socket, users))
        });

    let post_list = shopping_list.clone();
    let post_users = users.clone();
    let update_item_route = warp::path!("v1" / "update")
        .and(warp::post())
        .and(warp::body::json())
        .and(warp::any().map(move || Arc::clone(&post_list)))
        .and(warp::any().map(move || post_users.clone()))
        .and_then(update_shopping_item);

    let delete_checked_list = shopping_list.clone();
    let delete_checked_users = users.clone();
    let delete_checked_route = warp::path!("v1" / "delete-checked")
        .and(warp::delete())
        .and(warp::any().map(move || Arc::clone(&delete_checked_list)))
        .and(warp::any().map(move || delete_checked_users.clone()))
        .and_then(delete_checked);

    let delete_all_list = shopping_list.clone();
    let delete_all_users = users.clone();
    let delete_all_route = warp::path!("v1" / "delete-all")
        .and(warp::delete())
        .and(warp::any().map(move || Arc::clone(&delete_all_list)))
        .and(warp::any().map(move || delete_all_users.clone()))
        .and_then(delete_all);

    // Combine routes
    let routes = websocket
        .or(update_item_route)
        .or(delete_checked_route)
        .or(delete_all_route);

    {
        let state_path = state_path.clone();
        let shopping_list = shopping_list.clone();
        tokio::spawn(async move {
            tokio::signal::ctrl_c().await.unwrap();
            info!("Received signal, stopping server.");
            save_state(&state_path.clone(), shopping_list.clone()).await.expect("Failed saving data to state file");
            std::process::exit(0);
        });
    }

    {
        let state_path = state_path.clone();
        let shopping_list = shopping_list.clone();
        tokio::spawn(async move {
            loop {
                save_state(&state_path, shopping_list.clone()).await.expect("Failed saving data to state file");
                tokio::time::sleep(Duration::from_secs(300)).await;
            }
        });
    }

    // Start server
    warp::serve(routes).run(([0, 0, 0, 0], 3030)).await;
}

fn load_state(state_path: &PathBuf) -> Result<HashMap<Uuid, ShoppingItem>, tokio::io::Error> {
    if Path::new(&state_path).exists() {
        let file = File::open(state_path).expect("Failed to open state file.");
        let reader = BufReader::new(file);
        let data: HashMap<Uuid, ShoppingItem> = serde_json::from_reader(reader)?;
        info!("Loaded data from disk.");
        Ok(data)
    } else {
        info!("State file missing, loading intially empty State.");
        Ok(HashMap::new())
    }
}

async fn save_state(state_path: &PathBuf, shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<(), tokio::io::Error> {
    let file = File::create(&state_path)?;
    let parent_dir = state_path.parent().expect("Invalid dir path");
    let _ = fs::create_dir_all(parent_dir).await;
    let writer = BufWriter::new(file);
    serde_json::to_writer_pretty(writer, &*shopping_list.read().unwrap())?;
    info!("Saved back data to disk");
    Ok(())
}
