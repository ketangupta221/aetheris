/*
 * :app — the single Application class for Aetheris Planner.
 *
 * Responsibilities per the spec:
 *
 *   Task 3.1 — install the zero-network runtime gate from :core:common
 *   *before* any other component initializes. The SocketFactory override
 *   throws SecurityException on any socket created outside the consented
 *   download scope (Req 5.1 / 5.2 / 5.3 / 5.4, design §1.2 + §2.4).
 *
 *   Task 6.3 — `@HiltAndroidApp` marks this as the root of the Hilt DI
 *   graph. The activity, and later every ViewModel / WorkManager worker,
 *   resolve their bindings via the SingletonComponent rooted here.
 *
 * Keeping `NetworkDenyingSocketFactory.install()` as the first call in
 * `onCreate()` is intentional: even though `@HiltAndroidApp` generates a
 * base class that does its own init in `super.onCreate()`, the socket gate
 * must be armed before Hilt (or any other Android subsystem reachable via
 * `ContentProvider.onCreate`) has a chance to touch the network.
 */
package dev.aetheris.planner.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.aetheris.planner.core.common.network.NetworkDenyingSocketFactory

/**
 * Application entry point. Installs [NetworkDenyingSocketFactory] before
 * `super.onCreate()` so no other component can open a socket on process
 * startup. The install call is idempotent: if some harness has already
 * installed the gate (for example Robolectric re-running the Application
 * lifecycle across tests) the second call is a no-op.
 */
@HiltAndroidApp
public class PlannerApplication : Application() {
    override fun onCreate() {
        // Install BEFORE super.onCreate() so any static initializers that
        // Android (or Hilt's generated base class) trigger during super's
        // work cannot open a socket before the gate is in place.
        NetworkDenyingSocketFactory.install()
        super.onCreate()
    }
}
