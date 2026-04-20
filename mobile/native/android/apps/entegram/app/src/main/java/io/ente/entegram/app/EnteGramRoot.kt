package io.ente.entegram.app

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun EnteGramRoot() {
    EnteGramTheme {
        AppSessionGate()
    }
}

@Composable
private fun AppSessionGate(
    viewModel: AppSessionViewModel = hiltViewModel(),
) {
    val sessionState = viewModel.uiState.collectAsStateWithLifecycle()
    AppNavigation(
        sessionState = sessionState.value,
        onAuthComplete = viewModel::refreshSession,
        onSignOut = viewModel::signOut,
    )
}

@Preview(showBackground = true)
@Composable
private fun EnteGramRootPreview() {
    EnteGramTheme {
        AppNavigation(
            sessionState = AppSessionUiState.SignedOut,
            onAuthComplete = {},
            onSignOut = {},
        )
    }
}
