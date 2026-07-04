use futures::StreamExt;
use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use tokio_tungstenite::connect_async;
use uuid::Uuid;

use justshop_backend::api::{update, AppState, Users};
use justshop_backend::app;
use justshop_backend::shopping_list::ShoppingItem;

#[tokio::test]
async fn websocket_receives_broadcast() {
    let state = AppState {
        shopping_list: Arc::new(RwLock::new(HashMap::new())),
        users: Users::default(),
    };

    // Serve on an ephemeral loopback port.
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    let server_app = app(state.clone());
    tokio::spawn(async move {
        axum::serve(listener, server_app).await.unwrap();
    });

    // Connect a real websocket client.
    let url = format!("ws://{}/v2/ws?list_name=junkyard", addr);
    let (mut ws, _resp) = connect_async(url).await.unwrap();

    // On connect the server sends the initial snapshot (an empty list).
    let first = ws.next().await.expect("stream ended").expect("ws error");
    assert!(first.is_text());

    // Mutate shared state and broadcast, exactly as the POST handler does.
    let id = Uuid::new_v4();
    let new_item: ShoppingItem = serde_json::from_str(&format!(
        r#"{{"id":"{}","content":"milk","checked":false,"timestamp":0}}"#,
        id
    ))
    .unwrap();
    state
        .shopping_list
        .write()
        .unwrap()
        .get_mut("junkyard")
        .expect("list created on connect")
        .insert(id, new_item);
    update(state.shopping_list.clone(), state.users.clone());

    // The client should receive the updated snapshot containing the item.
    let msg = ws.next().await.expect("stream ended").expect("ws error");
    let text = msg.to_text().expect("expected text frame");
    assert!(text.contains("milk"), "frame missing content: {text}");
    assert!(text.contains(&id.to_string()), "frame missing uuid: {text}");
}
