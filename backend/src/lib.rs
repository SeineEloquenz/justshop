pub mod api;
pub mod auth;
pub mod shopping_list;
pub mod state;

use axum::middleware;
use axum::routing::{delete, get, post};
use axum::Router;

use crate::api::AppState;

pub fn app(state: AppState) -> Router {
    let protected = Router::new()
        .route("/v2/ws", get(api::ws_handler))
        .route("/v2/update", post(api::update_shopping_item))
        .route("/v2/delete-checked", delete(api::delete_checked))
        .route("/v2/delete-all", delete(api::delete_all))
        .route("/v2/auth/logout", post(auth::logout))
        .route_layer(middleware::from_fn_with_state(state.clone(), auth::require_session));

    Router::new()
        .route("/v2/auth-config", get(auth::auth_config))
        .route("/v2/auth/oidc/start", post(auth::oidc_start))
        .route("/v2/auth/oidc/finish", post(auth::oidc_finish))
        .merge(protected)
        .with_state(state)
}
