use clap::Parser;
use std::future::pending;
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};
use tokio::signal::unix::{signal, Signal, SignalKind};
use tokio::sync::Notify;
use tokio::time::{interval, Duration};
use tracing::{error, info, warn};

use justshop_backend::auth::{self, OidcAuth, OidcConfig};
use justshop_backend::shopping_list::ShoppingListContent;
use justshop_backend::{api, state, app};

#[derive(Parser, Debug, Clone)]
struct Cli {
    #[arg(long, default_value = "127.0.0.1:3030")]
    listen: String,

    #[arg(short, long, default_value = "justshop.json")]
    state_file: PathBuf,

    #[arg(long, default_value = "justshop-sessions.json")]
    session_file: PathBuf,

    /// OIDC issuer URL. Enables OIDC login when set.
    #[arg(long, requires_all = ["oidc_client_id", "oidc_client_secret_file"])]
    oidc_issuer: Option<String>,

    #[arg(long)]
    oidc_client_id: Option<String>,

    #[arg(long)]
    oidc_client_secret_file: Option<PathBuf>,

    #[arg(long, value_delimiter = ',', default_value = "openid,profile")]
    oidc_scopes: Vec<String>,

    /// Days a login session may go unused before it expires.
    #[arg(long, default_value_t = 90)]
    session_idle_days: u64,
}

async fn sessions_changed(oidc: &Option<Arc<OidcAuth>>) {
    match oidc {
        Some(oidc) => oidc.sessions_changed.notified().await,
        None => pending().await,
    }
}

async fn save_sessions(path: &Path, oidc: &Option<Arc<OidcAuth>>) {
    if let Some(oidc) = oidc {
        if let Err(e) = state::save_state(path, &oidc.sessions).await {
            error!("Saving sessions failed: {e}");
        }
    }
}

async fn persistence_task(
    state_path: PathBuf,
    session_path: PathBuf,
    shopping_list: Arc<RwLock<ShoppingListContent>>,
    list_changed: Arc<Notify>,
    oidc: Option<Arc<OidcAuth>>,
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
                if let Some(oidc) = &oidc {
                    oidc.sessions.write().unwrap().prune(auth::now(), oidc.session_idle_timeout);
                }
                save_sessions(&session_path, &oidc).await;
            }
            _ = list_changed.notified() => {
                if let Err(e) = state::save_state(&state_path, &shopping_list).await {
                    error!("Saving state failed: {e}");
                }
            }
            _ = sessions_changed(&oidc) => save_sessions(&session_path, &oidc).await,
            _ = sigint.recv() => break,
            _ = sigterm.recv() => break,
        }
    }

    info!("Received signal, saving state and shutting down.");
    save_sessions(&session_path, &oidc).await;
    if let Err(e) = state::save_state(&state_path, &shopping_list).await {
        error!("Saving state on shutdown failed: {e}");
        std::process::exit(1);
    }
    std::process::exit(0);
}

async fn load_oidc(cli: &Cli) -> Option<Arc<OidcAuth>> {
    let issuer = cli.oidc_issuer.clone()?;
    let secret_file = cli.oidc_client_secret_file.as_ref().unwrap();
    let client_secret = tokio::fs::read_to_string(secret_file)
        .await
        .expect("Failed to read OIDC client secret file")
        .trim()
        .to_owned();
    let sessions = state::load_state(&cli.session_file).await.expect("Failed to load session file.");

    let config = OidcConfig {
        issuer,
        client_id: cli.oidc_client_id.clone().unwrap(),
        client_secret,
        scopes: cli.oidc_scopes.clone(),
        session_idle_timeout: cli.session_idle_days * 86400,
    };
    let oidc = Arc::new(OidcAuth::new(config, sessions).expect("Invalid OIDC configuration"));

    let discovery = oidc.clone();
    tokio::spawn(async move {
        if discovery.metadata(false).await.is_err() {
            warn!("OIDC provider not reachable yet, retrying on first login");
        }
    });
    Some(oidc)
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().init();

    let cli = Cli::parse();

    let shopping_list = state::load_state(&cli.state_file).await.expect("Failed to load state file.");
    let shopping_list = Arc::new(RwLock::new(shopping_list));
    let list_changed = Arc::new(Notify::new());
    let oidc = load_oidc(&cli).await;

    let app_state = api::AppState {
        shopping_list: shopping_list.clone(),
        users: api::Users::default(),
        list_changed: list_changed.clone(),
        oidc: oidc.clone(),
    };

    let sigint = signal(SignalKind::interrupt()).expect("Failed to install SIGINT handler");
    let sigterm = signal(SignalKind::terminate()).expect("Failed to install SIGTERM handler");
    tokio::spawn(persistence_task(cli.state_file, cli.session_file, shopping_list, list_changed, oidc, sigint, sigterm));

    let listener = tokio::net::TcpListener::bind(&cli.listen)
        .await
        .expect("Failed to bind to address");
    axum::serve(listener, app(app_state)).await.expect("Server error");
}
