package nz.eloque.justshop.ui.settings

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.justshop.R


@Composable
fun SettingsView(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsState()

    Column(modifier = modifier) {
        Section(heading = stringResource(R.string.settings_section_server)) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SubmittableTextField(
                    label = stringResource(R.string.server_url),
                    imageVector = Icons.Filled.Save,
                    initialValue = uiState.serverUrl,
                    clearOnSubmit = false,
                    inputValidator = URLUtil::isValidUrl,
                    onSubmit = settingsViewModel::updateServerUrl
                )
                SubmittableTextField(
                    label = stringResource(R.string.list_name),
                    imageVector = Icons.Filled.Save,
                    initialValue = uiState.listName,
                    clearOnSubmit = false,
                    onSubmit = settingsViewModel::updateListName
                )
            }
        }
        Section(heading = stringResource(R.string.settings_section_authentication)) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SubmittableTextField(
                    label = stringResource(R.string.api_user_name),
                    imageVector = Icons.Filled.Save,
                    initialValue = uiState.userName,
                    clearOnSubmit = false,
                    onSubmit = settingsViewModel::updateUserName
                )
                SubmittableTextField(
                    label = stringResource(R.string.api_password),
                    imageVector = Icons.Filled.Save,
                    initialValue = uiState.password,
                    clearOnSubmit = false,
                    hidden = true,
                    onSubmit = settingsViewModel::updatePassword
                )
            }
        }
    }
}
