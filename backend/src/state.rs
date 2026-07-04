use tokio::fs;
use tracing::info;
use std::collections::HashMap;
use std::fs::File;
use std::path::{Path, PathBuf};
use std::io::{BufReader, BufWriter, Write};
use std::sync::RwLock;

use crate::shopping_list::ShoppingListContent;

pub fn load_state(state_path: &Path) -> Result<ShoppingListContent, tokio::io::Error> {
    if state_path.exists() {
        let file = File::open(state_path)?;
        let reader = BufReader::new(file);
        let data: ShoppingListContent = serde_json::from_reader(reader)?;
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
    if let Some(parent) = state_path.parent() {
        fs::create_dir_all(parent).await?;
    }

    let mut tmp_path = state_path.as_os_str().to_owned();
    tmp_path.push(".tmp");
    let tmp_path = PathBuf::from(tmp_path);

    let file = File::create(&tmp_path)?;
    let mut writer = BufWriter::new(file);
    serde_json::to_writer_pretty(&mut writer, &*shopping_list.read().unwrap())?;
    writer.flush()?;
    writer.into_inner()?.sync_all()?;

    std::fs::rename(&tmp_path, state_path)?;
    info!("Saved state to disk.");
    Ok(())
}
