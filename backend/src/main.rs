use clap::Parser;
use std::sync::{Arc, RwLock};
use std::collections::HashMap;
use serde::{Deserialize, Serialize};
use std::path::{Path,PathBuf};
use std::io:: {BufReader,BufWriter};
use std::fs::File;
use tokio::time::Duration;
use uuid::Uuid;
use warp::{Filter, Rejection, Reply};

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

// Handler for GET endpoint
async fn get_shopping_list(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let shopping_list = shopping_list.read().unwrap();
    Ok(warp::reply::json(&ShoppingList(shopping_list.clone())))
}

// Handler for POST item endpoint
async fn update_shopping_item(updated_item: ShoppingItem, shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.insert(updated_item.id, updated_item);
    Ok(warp::reply())
}

// Handler for DELETE checked endpoint
async fn delete_checked(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.retain(|_, value| !value.checked);
    Ok(warp::reply())
}

// Handler for DELETE all endpoint
async fn delete_all(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.clear();
    Ok(warp::reply())
}

#[tokio::main]
async fn main() {
    // Initialize empty shopping list

    let cli = Cli::parse();

    let state_path = cli.state_file;
    let shopping_list = load_state(&state_path).expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));

    let get_list = shopping_list.clone();
    // Define routes
    let get_route = warp::path!("current")
        .and(warp::get())
        .and(warp::any().map(move || Arc::clone(&get_list)))
        .and_then(get_shopping_list);

    let post_list = shopping_list.clone();
    let update_item_route = warp::path!("update")
        .and(warp::post())
        .and(warp::body::json())
        .and(warp::any().map(move || Arc::clone(&post_list)))
        .and_then(update_shopping_item);

    let delete_checked_list = shopping_list.clone();
    let delete_checked_route = warp::path!("delete-checked")
        .and(warp::delete())
        .and(warp::any().map(move || Arc::clone(&delete_checked_list)))
        .and_then(delete_checked);

    let delete_all_list = shopping_list.clone();
    let delete_all_route = warp::path!("delete-all")
        .and(warp::delete())
        .and(warp::any().map(move || Arc::clone(&delete_all_list)))
        .and_then(delete_all);

    // Combine routes
    let routes = get_route
        .or(update_item_route)
        .or(delete_checked_route)
        .or(delete_all_route);

    {
        let state_path = state_path.clone();
        let shopping_list = shopping_list.clone();
        ctrlc::set_handler(move || {
            save_state(&state_path.clone(), shopping_list.clone()).expect("Failed saving data to state file");
            std::process::exit(0);
        }).expect("Could not set sigterm handler");
    }

    {
        let state_path = state_path.clone();
        let shopping_list = shopping_list.clone();
        tokio::spawn(async move {
            loop {
                save_state(&state_path, shopping_list.clone()).expect("Failed saving data to state file");
                tokio::time::sleep(Duration::from_secs(5)).await;
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
        Ok(data)
    } else {
        Ok(HashMap::new())
    }
}

fn save_state(state_path: &PathBuf, shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<(), tokio::io::Error> {
    let file = File::create(&state_path)?;
    let writer = BufWriter::new(file);
    serde_json::to_writer_pretty(writer, &*shopping_list.read().unwrap())?;
    Ok(())
}
