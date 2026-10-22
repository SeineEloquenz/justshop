package nz.eloque.justshop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.compose_kit.components.Section
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.justshop.R
import nz.eloque.justshop.model.auth.AuthMode


@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Section(heading = stringResource(R.string.settings_section_server)) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(uiState.serverUrl)
                Text(
                    when (uiState.authMode) {
                        AuthMode.NONE -> stringResource(R.string.auth_status_none)
                        AuthMode.BASIC -> stringResource(R.string.auth_status_basic, uiState.userName)
                        AuthMode.OIDC -> stringResource(R.string.auth_status_oidc, uiState.userName)
                    }
                )
                OutlinedButton(
                    onClick = settingsViewModel::changeServer,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.change_server)) }
            }
        }
        Section(heading = stringResource(R.string.settings_section_list)) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SubmittableTextField(
                    label = stringResource(R.string.list_name),
                    imageVector = Icons.Filled.Save,
                    initialValue = uiState.listName,
                    clearOnSubmit = false,
                    onSubmit = settingsViewModel::updateListName
                )
            }
        }
    }
}
