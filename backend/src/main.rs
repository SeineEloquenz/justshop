use std::sync::{Arc, RwLock};
use serde::{Deserialize, Serialize};
use warp::{Filter, Rejection, Reply};

#[derive(Clone, Debug, Serialize, Deserialize)]
struct ShoppingItem {
    id: u128,
    content: String,
    checked: bool,
}

#[derive(Debug, Serialize, Deserialize)]
struct ShoppingList(Vec<ShoppingItem>);

// Handler for GET endpoint
async fn get_shopping_list(shopping_list: Arc<RwLock<Vec<ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let shopping_list = shopping_list.read().unwrap();
    Ok(warp::reply::json(&ShoppingList(shopping_list.clone())))
}

// Handler for POST endpoint
async fn update_shopping_list(new_list: ShoppingList, shopping_list: Arc<RwLock<Vec<ShoppingItem>>>) -> Result<impl Reply, Rejection> {
    let mut list = shopping_list.write().unwrap();
    *list = new_list.0;
    Ok(warp::reply())
}

#[tokio::main]
async fn main() {
    // Initialize empty shopping list
    let shopping_list = Arc::new(RwLock::new(Vec::new()));

    let get_list = shopping_list.clone();
    // Define routes
    let get_route = warp::path!("current")
        .and(warp::get())
        .and(warp::any().map(move || Arc::clone(&get_list)))
        .and_then(get_shopping_list);

    let post_list = shopping_list.clone();
    let post_route = warp::path!("update")
        .and(warp::post())
        .and(warp::body::json())
        .and(warp::any().map(move || Arc::clone(&post_list)))
        .and_then(update_shopping_list);

    // Combine routes
    let routes = get_route.or(post_route);

    // Start server
    warp::serve(routes).run(([127, 0, 0, 1], 3030)).await;
}