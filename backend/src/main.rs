pub mod api;
pub mod shopping_list;

use clap::Parser;
use shopping_list::ShoppingItem;
use tokio::fs;
use std::sync::{Arc, RwLock};
use std::collections::HashMap;
use std::path::{Path,PathBuf};
use std::io:: {BufReader,BufWriter};
use std::fs::File;
use tokio::time::Duration;
use tracing::info;
use uuid::Uuid;
use warp::Filter;

#[derive(Parser, Debug, Clone)]
struct Cli {
    #[arg(short, long, default_value = "justshop.json")]
    state_file: PathBuf,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().init();

    // Initialize empty shopping list

    let cli = Cli::parse();

    let state_path = cli.state_file;
    let shopping_list = load_state(&state_path).expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));

    let users = api::Users::default();

    let ws_users = users.clone();
    let websocket = warp::path!("v1" / "ws")
        .and(warp::ws())
        .and(warp::any().map(move || ws_users.clone()))
        .map(|ws: warp::ws::Ws, users| {
            ws.on_upgrade(move |socket| api::user_connected(socket, users))
        });

    let post_list = shopping_list.clone();
    let post_users = users.clone();
    let update_item_route = warp::path!("v1" / "update")
        .and(warp::post())
        .and(warp::body::json())
        .and(warp::any().map(move || Arc::clone(&post_list)))
        .and(warp::any().map(move || post_users.clone()))
        .and_then(api::update_shopping_item);

    let delete_checked_list = shopping_list.clone();
    let delete_checked_users = users.clone();
    let delete_checked_route = warp::path!("v1" / "delete-checked")
        .and(warp::delete())
        .and(warp::any().map(move || Arc::clone(&delete_checked_list)))
        .and(warp::any().map(move || delete_checked_users.clone()))
        .and_then(api::delete_checked);

    let delete_all_list = shopping_list.clone();
    let delete_all_users = users.clone();
    let delete_all_route = warp::path!("v1" / "delete-all")
        .and(warp::delete())
        .and(warp::any().map(move || Arc::clone(&delete_all_list)))
        .and(warp::any().map(move || delete_all_users.clone()))
        .and_then(api::delete_all);

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
