use tokio::fs::{self, File};
use tokio::io::AsyncWriteExt;
use tracing::info;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::RwLock;

use crate::shopping_list::ShoppingListContent;

pub async fn load_state(state_path: &Path) -> Result<ShoppingListContent, tokio::io::Error> {
    if state_path.exists() {
        let bytes = fs::read(state_path).await?;
        let data: ShoppingListContent = serde_json::from_slice(&bytes)?;
        info!("Loaded data from disk.");
        Ok(data)
    } else {
        info!("State file missing, starting with an empty state.");
        Ok(HashMap::new())
    }
}

pub async fn save_state(
    state_path: &Path,
    shopping_list: &RwLock<ShoppingListContent>,
) -> Result<(), tokio::io::Error> {
    let bytes = serde_json::to_vec_pretty(&*shopping_list.read().unwrap())?;

    if let Some(parent) = state_path.parent() {
        fs::create_dir_all(parent).await?;
    }

    let mut tmp_path = state_path.as_os_str().to_owned();
    tmp_path.push(".tmp");
    let tmp_path = PathBuf::from(tmp_path);

    let mut file = File::create(&tmp_path).await?;
    file.write_all(&bytes).await?;
    file.sync_all().await?;
    drop(file);

    fs::rename(&tmp_path, state_path).await?;
    info!("Saved state to disk.");
    Ok(())
}
