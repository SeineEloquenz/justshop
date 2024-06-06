use tokio::fs;
use tracing::info;
use std::collections::HashMap;
use std::fs::File;
use std::path::{Path,PathBuf};
use std::io:: {BufReader,BufWriter};
use std::sync::{Arc,RwLock};
use uuid::Uuid;

use crate::shopping_list::ShoppingItem;

pub fn load_state(state_path: &PathBuf) -> Result<HashMap<Uuid, ShoppingItem>, tokio::io::Error> {
    if Path::new(&state_path).exists() {
        let file = File::open(state_path).expect("Failed to open state file.");
        let reader = BufReader::new(file);
        let data: HashMap<Uuid, ShoppingItem> = serde_json::from_reader(reader)?;
        info!("Loaded data from disk.");
        Ok(data)
    } else {
        info!("State file missing, loading intially empty State.");
        Ok(HashMap::new())
    }
}

pub async fn save_state(state_path: &PathBuf, shopping_list: Arc<RwLock<HashMap<Uuid, ShoppingItem>>>) -> Result<(), tokio::io::Error> {
    let file = File::create(&state_path)?;
    let parent_dir = state_path.parent().expect("Invalid dir path");
    let _ = fs::create_dir_all(parent_dir).await;
    let writer = BufWriter::new(file);
    serde_json::to_writer_pretty(writer, &*shopping_list.read().unwrap())?;
    info!("Saved back data to disk");
    Ok(())
}
