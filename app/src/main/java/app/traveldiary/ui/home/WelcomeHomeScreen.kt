package app.traveldiary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.traveldiary.R
import app.traveldiary.ui.common.EmptyState

/** First launch only: asks where the user lives, which can be skipped. */
@Composable
fun WelcomeHomeScreen(onChooseHome: () -> Unit, onSkip: () -> Unit) {
    Scaffold { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Outlined.Home,
                title = stringResource(R.string.welcome_home_title),
                message = stringResource(R.string.welcome_home_message),
                action = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Button(onClick = onChooseHome) { Text(stringResource(R.string.welcome_home_choose)) }
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.welcome_home_skip)) }
                    }
                },
            )
        }
    }
}
