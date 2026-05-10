/*
 * :app — Timeline placeholder Composable (Task 6.1).
 *
 * Phase 0 scope: this screen is a blank slate that proves the Compose +
 * Navigation + Material 3 theme pipeline works end-to-end. The real
 * Timeline_View (hour-rail, Time_Block rendering, overlap layout, current-
 * time indicator, horizontal swipe day navigation) lands in Phase 1
 * Task 20 per design §2.2 / §8 Phase 1.
 *
 * The placeholder intentionally references Phase 1 Task 20 and §8 so a
 * future contributor wondering "why is this empty?" finds the pointer
 * immediately.
 */
package dev.aetheris.planner.app.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Empty Timeline screen. Rendered as the main destination of the NavHost
 * once onboarding is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TimelinePlaceholder(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "Today") })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Aetheris Planner",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your day, offline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Timeline arrives in Phase 1.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
