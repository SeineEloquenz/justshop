package nz.eloque.justshop.ui.settings

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nz.eloque.justshop.R
import nz.eloque.justshop.ui.components.SubmittableTextField


@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsState()

    Column(
        modifier = modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubmittableTextField(
            label = { Text(stringResource(R.string.server_url)) },
            imageVector = Icons.Filled.Save,
            initialValue = uiState.serverUrl,
            clearOnSubmit = false,
            inputValidator = URLUtil::isValidUrl,
            onSubmit = settingsViewModel::updateServerUrl
        )
        SubmittableTextField(
            label = { Text(stringResource(R.string.sync_interval)) },
            imageVector = Icons.Filled.Save,
            initialValue = uiState.syncInterval.toString(),
            clearOnSubmit = false,
            inputValidator = { it.toLongOrNull() != null },
            onSubmit = { settingsViewModel.updateSyncInterval(it.toLong()) }
        )
    }
}