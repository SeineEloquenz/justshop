use axum::body::Body;
use axum::http::{Request, StatusCode};
use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use tower::ServiceExt; // for `oneshot`
use uuid::Uuid;

use justshop_backend::api::{AppState, Users};
use justshop_backend::app;
use justshop_backend::shopping_list::ShoppingItem;

fn test_state() -> AppState {
    AppState {
        shopping_list: Arc::new(RwLock::new(HashMap::new())),
        users: Users::default(),
    }
}

fn item(id: Uuid, checked: bool) -> ShoppingItem {
    // ShoppingItem has private fields, so build it through serde.
    serde_json::from_str(&format!(
        r#"{{"id":"{}","content":"x","checked":{},"timestamp":0}}"#,
        id, checked
    ))
    .unwrap()
}

#[tokio::test]
async fn update_inserts_item_into_existing_list() {
    let state = test_state();
    // Pre-create the list; the handler only inserts into an existing list
    // (the websocket connect path is what creates lists).
    state
        .shopping_list
        .write()
        .unwrap()
        .insert("junkyard".to_string(), HashMap::new());

    let id = Uuid::new_v4();
    let body = format!(
        r#"{{"id":"{}","content":"milk","checked":false,"timestamp":0}}"#,
        id
    );

    let response = app(state.clone())
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/v2/update?list_name=junkyard")
                .header("content-type", "application/json")
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);

    let lists = state.shopping_list.read().unwrap();
    let list = lists.get("junkyard").expect("list should exist");
    assert!(list.contains_key(&id));
    assert!(!list.get(&id).unwrap().checked);
}

#[tokio::test]
async fn delete_checked_removes_only_checked_items() {
    let state = test_state();
    let checked_id = Uuid::new_v4();
    let unchecked_id = Uuid::new_v4();
    {
        let mut lists = state.shopping_list.write().unwrap();
        let list = lists.entry("junkyard".to_string()).or_default();
        list.insert(checked_id, item(checked_id, true));
        list.insert(unchecked_id, item(unchecked_id, false));
    }

    let response = app(state.clone())
        .oneshot(
            Request::builder()
                .method("DELETE")
                .uri("/v2/delete-checked?list_name=junkyard")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);

    let lists = state.shopping_list.read().unwrap();
    let list = lists.get("junkyard").unwrap();
    assert!(!list.contains_key(&checked_id));
    assert!(list.contains_key(&unchecked_id));
}

#[tokio::test]
async fn delete_all_clears_the_list() {
    let state = test_state();
    let id = Uuid::new_v4();
    state
        .shopping_list
        .write()
        .unwrap()
        .entry("junkyard".to_string())
        .or_default()
        .insert(id, item(id, false));

    let response = app(state.clone())
        .oneshot(
            Request::builder()
                .method("DELETE")
                .uri("/v2/delete-all?list_name=junkyard")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    assert!(state.shopping_list.read().unwrap().get("junkyard").unwrap().is_empty());
}
