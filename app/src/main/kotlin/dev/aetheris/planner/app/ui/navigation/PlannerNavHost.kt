/*
 * :app — Compose Navigation graph (Task 6.1 + 6.2).
 *
 * Single-activity architecture per design §1: [MainActivity] hosts a single
 * [NavHost] that swaps between destinations. Phase 0 has two routes:
 *
 *   - [PlannerRoutes.Onboarding] — first-run welcome screen (Task 6.2).
 *   - [PlannerRoutes.Timeline] — blank Timeline placeholder (Task 6.1).
 *
 * The Phase 0 start destination is always Onboarding because Task 6.2's
 * acceptance criteria call for a "single screen Welcome with Continue
 * button". Phase 1 Task 24 replaces this with a persisted "has completed
 * onboarding" flag so returning users boot straight into Timeline.
 */
package dev.aetheris.planner.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.aetheris.planner.app.ui.onboarding.OnboardingScreen
import dev.aetheris.planner.app.ui.timeline.TimelinePlaceholder

/** String route identifiers for the Compose Navigation graph. */
public object PlannerRoutes {
    /** First-run welcome screen (Task 6.2). */
    public const val ONBOARDING: String = "onboarding"

    /** Blank Timeline placeholder (Task 6.1). */
    public const val TIMELINE: String = "timeline"
}

/**
 * Top-level NavHost. Hosted by [dev.aetheris.planner.app.MainActivity]
 * inside [dev.aetheris.planner.app.ui.theme.AetherisTheme].
 */
@Composable
public fun PlannerNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(PlannerRoutes.ONBOARDING) {
            OnboardingScreen(
                onContinue = {
                    // Pop onboarding off the back stack when navigating to
                    // Timeline so pressing Back from Timeline exits the app
                    // rather than returning to Welcome — matches the
                    // "first-run only" semantics of Req 21.1 even though
                    // the persisted "has completed" flag itself lives in
                    // Phase 1 Task 24.
                    navController.navigate(PlannerRoutes.TIMELINE) {
                        popUpTo(PlannerRoutes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(PlannerRoutes.TIMELINE) {
            TimelinePlaceholder()
        }
    }
}
