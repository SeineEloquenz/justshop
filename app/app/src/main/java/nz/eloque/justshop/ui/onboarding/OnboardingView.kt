package nz.eloque.justshop.ui.onboarding

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nz.eloque.justshop.R
import nz.eloque.justshop.model.auth.launchLogin

@Composable
fun OnboardingView(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.loginUrl) {
        uiState.loginUrl?.let {
            launchLogin(context, it)
            viewModel.loginLaunched()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            label = { Text(stringResource(R.string.server_url)) },
            singleLine = true,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.listName,
            onValueChange = viewModel::updateListName,
            label = { Text(stringResource(R.string.list_name)) },
            singleLine = true,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        when (uiState.step) {
            OnboardingStep.SERVER -> Button(
                onClick = viewModel::checkServer,
                enabled = !uiState.busy && URLUtil.isValidUrl(uiState.serverUrl) && uiState.listName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.next)) }

            OnboardingStep.BASIC_CREDENTIALS -> BasicCredentials(
                enabled = !uiState.busy,
                onSubmit = viewModel::submitBasic,
            )

            OnboardingStep.OIDC_SIGN_IN -> {
                Text(stringResource(R.string.onboarding_oidc_hint))
                Button(
                    onClick = viewModel::signIn,
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sign_in)) }
            }
        }

        if (uiState.busy) {
            CircularProgressIndicator()
        }
        uiState.error?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BasicCredentials(
    enabled: Boolean,
    onSubmit: (String, String) -> Unit,
) {
    var userName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Text(stringResource(R.string.onboarding_basic_hint))
    OutlinedTextField(
        value = userName,
        onValueChange = { userName = it },
        label = { Text(stringResource(R.string.api_user_name)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.api_password)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onSubmit(userName, password) },
        enabled = enabled && userName.isNotEmpty() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.next)) }
}
