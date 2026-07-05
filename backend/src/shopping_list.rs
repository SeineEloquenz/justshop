use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use uuid::Uuid;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ShoppingItem {
    pub id: Uuid,
    pub content: String,
    pub checked: bool,
    pub timestamp: i64,
}

pub type ShoppingListContent = HashMap<String, HashMap<Uuid, ShoppingItem>>;