/*
 * :app — MainActivity (Tasks 6.1 + 6.3).
 *
 * Single-activity architecture per design §1. Hosts the Compose [NavHost]
 * that switches between the onboarding stub and the Timeline placeholder.
 * @AndroidEntryPoint marks this as a Hilt injection target (Task 6.3), and
 * the field-injected [ThemePreferences] drives the Phase 0 theme.
 *
 * The activity itself is intentionally thin: any non-trivial logic (state
 * lifting, ViewModel wiring, deep-link handling, onboarding completion
 * flag) lands in later phases. What ships here is the minimum surface
 * area needed to (a) assemble the APK, (b) install on the Samsung S23,
 * and (c) give internal testers something to tap so Phase 0 exit
 * validation (Task 9) can sign off.
 *
 * Requirements:
 *  - Req 22.1 — respects persisted theme mode.
 *  - Req 22.2 — opts into Material You dynamic color on API 31+ when the
 *               user has not disabled it.
 *  - Req 21.1 — first route is the Welcome onboarding screen.
 */
package dev.aetheris.planner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.aetheris.planner.app.data.settings.ThemePreferences
import dev.aetheris.planner.app.ui.navigation.PlannerNavHost
import dev.aetheris.planner.app.ui.navigation.PlannerRoutes
import dev.aetheris.planner.app.ui.theme.AetherisTheme
import dev.aetheris.planner.app.ui.theme.ThemeMode
import javax.inject.Inject

/** Launcher activity — single-activity host for the Compose navigation graph. */
@AndroidEntryPoint
public class MainActivity : ComponentActivity() {

    @Inject
    internal lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let Compose draw edge-to-edge. The status and navigation bars are
        // configured as transparent in Theme.Aetheris.Splash so Compose-
        // rendered surfaces handle their own insets via WindowInsets APIs.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(
                initial = ThemeMode.FollowSystem,
            )
            val useDynamicColor by themePreferences.useDynamicColor.collectAsState(
                initial = ThemePreferences.DEFAULT_USE_DYNAMIC_COLOR,
            )

            AetherisTheme(
                themeMode = themeMode,
                useDynamicColor = useDynamicColor,
            ) {
                val navController = rememberNavController()
                PlannerNavHost(
                    navController = navController,
                    startDestination = PlannerRoutes.ONBOARDING,
                )
            }
        }
    }
}
