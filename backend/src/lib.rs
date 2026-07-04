pub mod api;
pub mod shopping_list;
pub mod state;

use axum::routing::{delete, get, post};
use axum::Router;

use crate::api::AppState;

pub fn app(state: AppState) -> Router {
    Router::new()
        .route("/v2/ws", get(api::ws_handler))
        .route("/v2/update", post(api::update_shopping_item))
        .route("/v2/delete-checked", delete(api::delete_checked))
        .route("/v2/delete-all", delete(api::delete_all))
        .with_state(state)
}
