use std::sync::{Arc, RwLock};
use std::collections::HashMap;
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use warp::{Filter, Rejection, Reply};

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

// Handler for DELETE all endpoint
async fn delete_checked(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.retain(|_, value| !value.checked);
    Ok(warp::reply())
}

// Handler for DELETE checked endpoint
async fn delete_all(shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    list.clear();
    Ok(warp::reply())
}

#[tokio::main]
async fn main() {
    // Initialize empty shopping list
    let shopping_list = Arc::new(RwLock::new(HashMap::new()));

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

    // Start server
    warp::serve(routes).run(([0, 0, 0, 0], 3030)).await;
}