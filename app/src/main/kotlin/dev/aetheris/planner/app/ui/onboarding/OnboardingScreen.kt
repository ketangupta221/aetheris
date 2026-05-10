/*
 * :app — Onboarding screen stub (Task 6.2).
 *
 * Phase 0 scope per the task:
 *   - Single screen with "Welcome" title and a Continue button that
 *     navigates to the Timeline destination.
 *   - Real onboarding content (local-only data model explanation, just-in-
 *     time permission rationale wrappers, first-task CTA) lands in Phase 1
 *     Task 24 per Req 21.1.
 *
 * We surface a short on-device privacy note even in this stub so the Phase 0
 * Firebase App Distribution build is not misleading to internal testers.
 *
 * Requirements touched:
 *   - Req 21.1 (first-run onboarding)
 */
package dev.aetheris.planner.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-run welcome screen. Displays a short privacy note and a single
 * Continue button that invokes [onContinue] to navigate to the Timeline.
 *
 * @param onContinue invoked when the user taps Continue. MainActivity wires
 *   this to a NavController.navigate that pops the onboarding destination
 *   off the back stack so pressing Back from Timeline exits the app rather
 *   than returning to the Welcome screen.
 * @param modifier optional Compose modifier; tests may pass a testTag.
 */
@Composable
public fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "Welcome to Aetheris Planner",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text =
                    "Everything you create lives on this device only — " +
                        "no servers, no cloud, no analytics.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Continue")
            }
        }
    }
}
