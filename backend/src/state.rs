use serde::de::DeserializeOwned;
use serde::Serialize;
use tokio::fs::{self, File};
use tokio::io::AsyncWriteExt;
use tracing::info;
use std::path::{Path, PathBuf};
use std::sync::RwLock;

pub async fn load_state<T: DeserializeOwned + Default>(state_path: &Path) -> Result<T, tokio::io::Error> {
    if state_path.exists() {
        let bytes = fs::read(state_path).await?;
        let data: T = serde_json::from_slice(&bytes)?;
        info!("Loaded {} from disk.", state_path.display());
        Ok(data)
    } else {
        info!("{} missing, starting with an empty state.", state_path.display());
        Ok(T::default())
    }
}

pub async fn save_state<T: Serialize>(
    state_path: &Path,
    state: &RwLock<T>,
) -> Result<(), tokio::io::Error> {
    let bytes = serde_json::to_vec_pretty(&*state.read().unwrap())?;

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
    info!("Saved {} to disk.", state_path.display());
    Ok(())
}
