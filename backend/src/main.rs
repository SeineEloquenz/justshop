use clap::Parser;
use std::sync::{Arc, RwLock};
use std::path::PathBuf;
use tokio::time::Duration;
use tracing::info;

use justshop_backend::{api, state, app};

#[derive(Parser, Debug, Clone)]
struct Cli {
    #[arg(short, long, default_value = "justshop.json")]
    state_file: PathBuf,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().init();

    let cli = Cli::parse();

    let state_path = cli.state_file;
    let shopping_list = state::load_state(&state_path).expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));

    let users = api::Users::default();

    let app_state = api::AppState {
        shopping_list: shopping_list.clone(),
        users: users.clone(),
    };

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
    let listener = tokio::net::TcpListener::bind("0.0.0.0:3030")
        .await
        .expect("Failed to bind to address");
    axum::serve(listener, app(app_state)).await.expect("Server error");
}
