pub mod api;
pub mod shopping_list;
pub mod state;

use clap::Parser;
use std::convert::Infallible;
use std::sync::{Arc, RwLock};
use std::path::PathBuf;
use tokio::time::Duration;
use tracing::info;
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
    let shopping_list = state::load_state(&state_path).expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));

    let users = api::Users::default();

    let websocket = warp::path!("v1" / "ws")
        .and(warp::ws())
        .and(with(shopping_list.clone()))
        .and(with(users.clone()))
        .map(|ws: warp::ws::Ws, shopping_list, users| {
            ws.on_upgrade(move |socket| api::user_connected(socket, shopping_list, users))
        });

    let update_item_route = warp::path!("v1" / "update")
        .and(warp::post())
        .and(warp::body::json())
        .and(with(shopping_list.clone()))
        .and(with(users.clone()))
        .and_then(api::update_shopping_item);

    let delete_checked_route = warp::path!("v1" / "delete-checked")
        .and(warp::delete())
        .and(with(shopping_list.clone()))
        .and(with(users.clone()))
        .and_then(api::delete_checked);

    let delete_all_route = warp::path!("v1" / "delete-all")
        .and(warp::delete())
        .and(with(shopping_list.clone()))
        .and(with(users.clone()))
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
            state::save_state(&state_path.clone(), shopping_list.clone()).await.expect("Failed saving data to state file");
            std::process::exit(0);
        });
    }

    {
        let state_path = state_path.clone();
        let shopping_list = shopping_list.clone();
        tokio::spawn(async move {
            loop {
                state::save_state(&state_path, shopping_list.clone()).await.expect("Failed saving data to state file");
                tokio::time::sleep(Duration::from_secs(300)).await;
            }
        });
    }

    // Start server
    warp::serve(routes).run(([0, 0, 0, 0], 3030)).await;
}

fn with<T: Clone + Send + Sync>(
    value: T,
) -> impl Filter<Extract = (T,), Error = Infallible> + Clone {
    let value = value.clone();
    warp::any().map(move || value.clone())
}