use clap::Parser;
use std::path::PathBuf;
use std::sync::{Arc, RwLock};
use tokio::signal::unix::{signal, Signal, SignalKind};
use tokio::time::{interval, Duration};
use tracing::{error, info};

use justshop_backend::shopping_list::ShoppingListContent;
use justshop_backend::{api, state, app};

#[derive(Parser, Debug, Clone)]
struct Cli {
    #[arg(short, long, default_value = "justshop.json")]
    state_file: PathBuf,
}

async fn persistence_task(
    state_path: PathBuf,
    shopping_list: Arc<RwLock<ShoppingListContent>>,
    mut sigint: Signal,
    mut sigterm: Signal,
) {
    let mut checkpoint = interval(Duration::from_secs(300));
    checkpoint.tick().await; // the first tick fires immediately; skip the redundant startup save

    loop {
        tokio::select! {
            _ = checkpoint.tick() => {
                if let Err(e) = state::save_state(&state_path, &shopping_list).await {
                    error!("Periodic save failed: {e}");
                }
            }
            _ = sigint.recv() => break,
            _ = sigterm.recv() => break,
        }
    }

    info!("Received signal, saving state and shutting down.");
    state::save_state(&state_path, &shopping_list)
        .await
        .expect("Failed to save state on shutdown");
    std::process::exit(0);
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().init();

    let cli = Cli::parse();
    let state_path = cli.state_file;

    let shopping_list = state::load_state(&state_path).await.expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));

    let app_state = api::AppState {
        shopping_list: shopping_list.clone(),
        users: api::Users::default(),
    };

    let sigint = signal(SignalKind::interrupt()).expect("Failed to install SIGINT handler");
    let sigterm = signal(SignalKind::terminate()).expect("Failed to install SIGTERM handler");
    tokio::spawn(persistence_task(state_path, shopping_list, sigint, sigterm));

    let listener = tokio::net::TcpListener::bind("0.0.0.0:3030")
        .await
        .expect("Failed to bind to address");
    axum::serve(listener, app(app_state)).await.expect("Server error");
}
