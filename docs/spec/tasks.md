# Implementation Plan: AI Daily Planner

## Overview

This plan converts the approved design (`design.md`) into discrete, phase-aligned coding steps. Each top-level task is numbered sequentially across the whole file (1..N) and belongs to a single phase. Sub-tasks use decimal notation (`X.Y`). Optional sub-tasks are marked with `*` immediately after the closing checkbox bracket (e.g. `- [ ]* 2.5`). Every property from design §6 that is in scope for a phase has its own first-class PBT sub-task and **is not optional**.

**Language / stack.** Kotlin on Android (as specified throughout design §1–§9). No pseudocode to resolve.

**Convention for references.** `_Requirements: X.Y_` refers to requirements by clause number (see `requirements.md`). `_Properties: PN_` refers to correctness properties from design §6. Tasks that do not implement a specific property do not carry a `_Properties:_` tag.

**Optional marking.** Tasks marked with `*` are nice-to-haves beyond the specified requirements and design — they can be skipped without breaking the phase. Essential implementation tasks and essential property tests are never marked optional.

---

## PHASE 0 — Scaffolding

**Goal.** A runnable empty shell with the module layout, manifest permission enforcement, CI, and an installable Firebase App Distribution artifact. Corresponds to design §8 Phase 0.

- [x] 1. Initialize Gradle multi-module project and module skeletons
  - [x] 1.1 Create all 20 modules with `build.gradle.kts` files
    - Modules: `:app`, `:core:common`, `:core:domain`, `:core:data`, `:core:scheduling`, `:core:notifications`, `:feature:timeline`, `:feature:tasks`, `:feature:chat`, `:feature:quickcapture`, `:feature:settings`, `:feature:habits`, `:feature:focus`, `:feature:rituals`, `:ai:router`, `:ai:nlparser`, `:ai:entity-resolver`, `:ai:llm`, `:ai:speech`, `:distribution:model-downloader`
    - `:app` sets `applicationId = "dev.aetheris.planner"` per design §2.1 Android identity
    - Every module's Kotlin `namespace` is `dev.aetheris.planner.<module-suffix>` (e.g. `dev.aetheris.planner.core.data`, `dev.aetheris.planner.ai.llm`)
    - Set `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34` per design §1
    - Launcher label `R.string.app_name` = "Aetheris Planner"
    - Settings.gradle.kts lists every module
    - _Requirements: 22.1, 29.2_
  - [x] 1.2 Wire Hilt, Compose BOM, Kotlin 1.9+, AGP 8+, KSP, Room, SQLCipher
    - Add Hilt plugin + KSP, Compose compiler, Room + KSP, SQLCipher (`net.zetetic:android-database-sqlcipher`), `androidx.sqlite`, `kotlinx-coroutines`, `kotlinx-datetime`, `kotlinx-serialization-json`, `jqwik` and `kotest-property` as test deps per design §1 and §9
    - Version catalog in `gradle/libs.versions.toml`
  - [x] 1.3 Configure signing configs (debug + release)
    - Debug keystore generated locally
    - Release signing reads `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from env (CI secrets); `KEY_ALIAS` value is `aetheris-release` per design §2.1 / §9.4
    - Fail fast with a clear error if any release env var is missing when `assembleRelease` is invoked
    - _Requirements: 29.2_
  - [x] 1.4 Configure Gradle Managed Devices
    - Device: Pixel 7 Pro API 34 Google APIs (per design §9.1)
    - `./gradlew pixel7proApi34GoogleApisDebugAndroidTest` runs instrumented tests with no physical device
  - [x] 1.5 Add Detekt + Android Lint configs
    - `detekt.yml` with standard ruleset plus custom rules stub
    - Lint severities elevated per design §9.1

- [x] 2. Implement manifest permission invariant enforcement
  - [x] 2.1 Create `:buildSrc:checkManifests` Gradle task
    - Text-scans every `**/src/**/AndroidManifest.xml` file
    - Fails the build if `android.permission.INTERNET` appears in any manifest outside `:distribution:model-downloader`
    - Same rule for `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — these must also be confined to `:distribution:model-downloader`
    - _Requirements: 5.8_
  - [x] 2.2 Wire `:buildSrc:checkManifests` into `./gradlew check`
    - Depends on `preBuild`, runs before `assemble`
    - CI fails if the task fails
    - _Requirements: 5.8_
  - [x] 2.3 Add custom Android Lint detector for INTERNET outside allowed module
    - Covers the post-merge manifest case (design §12.3)
    - Lint severity `Error`
    - _Requirements: 5.8_
  - [x] 2.4 Write P39 (Manifest permission invariant) test
    - Class `ManifestPermissionInvariantTest` in `:buildSrc` test source
    - Generates random manifest strings with an Arbitrary that sometimes inserts INTERNET at random module paths
    - Asserts: `checkManifests` returns success iff no INTERNET appears outside the allowed module
    - jqwik, 500 iterations per design §9.2
    - _Requirements: 5.8_
    - _Properties: P39_

- [x] 3. Implement runtime SocketFactory override (zero-network runtime enforcement)
  - [x] 3.1 Implement `NetworkDenyingSocketFactory` and install in `Application.onCreate`
    - `SSLSocketFactory` + `SocketFactory.setDefault` both overridden to throw `SecurityException` per design §5.3
    - `java.net.ProxySelector.setDefault` set to deny-all
    - Installed in `PlannerApplication.onCreate` before any other component initializes
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [x] 3.2 Add thread-local escape hatch for `:distribution:model-downloader`
    - Function `withConsentedDownload(block)` sets a `ThreadLocal<Boolean>` while the block runs; socket factory honors the flag and allows the socket
    - Module-scoped API — not exposed outside `:distribution:model-downloader`
    - _Requirements: 5.5, 24.1_
  - [x] 3.3 Write runtime scaffold for P18 (Zero network at runtime)
    - Robolectric test that asserts the socket factory throws on any attempted socket creation from outside the escape hatch
    - Full property coverage added in Phase 1 (task 25) when real operations exist
    - _Requirements: 5.1, 5.3_

- [x] 4. Set up CI pipeline (GitHub Actions)
  - [x] 4.1 Create `.github/workflows/ci.yml`
    - Stages in order: `lint` → `manifest-check` → `unit-test` → `connectedAndroidTest` (Gradle Managed Devices) → `assembleRelease` → (on `main` branch only) `uploadToFirebaseAppDistribution`
    - Cache Gradle + Android SDK between runs
  - [x] 4.2 Configure CI secrets
    - `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `aetheris-release`), `KEY_PASSWORD`, `FIREBASE_APP_ID`, `FIREBASE_CLI_TOKEN`
    - Document required repository secrets in `docs/ci.md`
  - [x] 4.3 Add build summary reporting
    - Test counts (unit + PBT + instrumented)
    - APK size (release) — surfaces unexpected growth early per design §12.1
    - Posted as a GitHub Actions job summary

- [ ] 5. Set up Firebase App Distribution
  - [ ] 5.1 Create Firebase project and link Android app
    - `google-services.json` checked in under `:app/`
    - Firebase project has **no** Analytics / Crashlytics / Messaging SDKs added per Req 5.7
    - _Requirements: 5.7_
  - [ ] 5.2 Add `com.google.firebase.appdistribution` Gradle plugin
    - Plugin applied only on `:app`, only in release variant
    - Release notes pulled from `CHANGELOG.md`
  - [ ] 5.3 Configure Firebase distribution groups
    - Group `internal-testers` includes the user's email
    - CI `uploadToFirebaseAppDistribution` task targets this group
  - [ ] 5.4 Document the install-link UX
    - `docs/install.md` with screenshots of the tester email + tap-to-install flow

- [x] 6. Create minimal app shell
  - [x] 6.1 Add `MainActivity` with a blank Timeline placeholder Composable
    - Single-activity architecture per design §1
    - Compose Navigation set up with route for `timeline`
  - [x] 6.2 Add onboarding screen stub
    - Single screen "Welcome" with Continue button that navigates to Timeline
    - Real content added in Phase 1 task 24
    - _Requirements: 21.1_
  - [x] 6.3 Wire Hilt entry points and empty DI graph
    - `@HiltAndroidApp` on `PlannerApplication`, `@AndroidEntryPoint` on `MainActivity`
    - Empty modules created per layer

- [x] 7. Theming shell
  - [x] 7.1 Implement light / dark / follow-system theme modes
    - Material 3 `ColorScheme` defined for both light and dark
    - Setting persisted in `DataStore` (stub store added)
    - _Requirements: 22.1_
  - [x] 7.2 Add Material You dynamic color stub for API 31+
    - `dynamicLightColorScheme` / `dynamicDarkColorScheme` used when `Build.VERSION.SDK_INT >= 31` and setting enabled
    - Full Material You polish deferred to Phase 2
    - _Requirements: 22.2_

- [ ] 8. Initialize GitHub repository and open-source governance
  - [x] 8.1 Create public GitHub repo named `aetheris` under the user's account
    - Visibility: public; default branch `main`
    - _Requirements: 29.3_
  - [x] 8.2 Check in `LICENSE` with the full PolyForm Noncommercial 1.0.0 text at repo root
    - _Requirements: 29.3_
  - [x] 8.3 Check in `NOTICE` listing third-party dependency licenses
    - Entries: llama.cpp MIT, whisper.cpp MIT, MediaPipe Apache-2.0, SQLCipher BSD-3, Room Apache-2.0, Compose Apache-2.0, Kotlin Apache-2.0
    - _Requirements: 29.4_
  - [x] 8.4 Write the initial `README.md`
    - Product description, brand etymology ("Aether" + "Intelligent System"), tagline "Offline AI Planner for your Daily Life", license summary pointing to `LICENSE`, local-run instructions, and a "Privacy" section stating all processing is on-device
    - _Requirements: 29.5_
  - [x] 8.5 Add `SECURITY.md`, `CONTRIBUTING.md`, and `CODE_OF_CONDUCT.md` at repo root
    - _Requirements: 29.6_
  - [x] 8.6 Host the privacy policy via GitHub Pages
    - `docs/privacy-policy.md` declares: no data collected, no data transmitted, no analytics / telemetry / crash reporting / advertising SDKs
    - Enable GitHub Pages serving from the `docs/` folder on `main`
    - Captured URL (e.g. `https://<user>.github.io/aetheris/privacy-policy`) is recorded so it can be linked from Settings → About in a future Phase 1 or Phase 3 task when the About screen lands
    - _Requirements: 29.7, 29.11_
  - [ ] 8.7 Configure branch protection rules on `main`
    - Require passing CI (the pipeline from task 4) before merge
    - Disallow force pushes
    - Require at least one review for pull requests
    - _Requirements: 29.8_
  - [ ] 8.8 Configure repository topics and description for discoverability
    - Topics: `android`, `offline-first`, `on-device-llm`, `productivity`, `kotlin`, `jetpack-compose`
    - Repository description: "Aetheris Planner — Offline AI Planner for your Daily Life"
    - _Requirements: 29.1, 29.5_

- [ ] 9. Phase 0 exit validation
  - [ ] 9.1 Verify CI is green end-to-end on `main`
    - All stages pass; P39 passes
  - [ ] 9.2 Verify user can tap-install the Phase 0 APK from Firebase App Distribution on the Samsung Galaxy S23
    - Documented in `docs/install.md` with a completed checklist
  - [ ] 9.3 Assemble signed release artifact tagged `v0.0-scaffold`
    - Release APK produced; size < 20 MB expected

---

## PHASE 1 — MVP: tasks you can touch, NL-only chat

**Goal.** The user can create, edit, complete, and delete tasks by tapping, and can type natural-language task inputs into a chat interface using the deterministic `NL_Parser` only — no LLM, no speech. Corresponds to design §8 Phase 1.

- [ ] 10. Implement `:core:domain` entities and repository interfaces
  - [ ] 10.1 Define core entities
    - `Task`, `Recurrence`, `Reminder`, `Conversation`, `ChatMessage`, `ActionPlan`, `Action`, `ConsentDecision`, `RoutingMetric` as pure Kotlin data classes with `kotlinx-datetime` timestamps per design §3.1
    - Value objects: `TaskId`, `ConversationId`, `MessageId`, `TagId`
    - _Requirements: 2.1, 2.3, 2.4, 3.1, 6.1, 9.1, 24.3_
  - [ ] 10.2 Define repository interfaces
    - `TaskRepository`, `RecurrenceRepository`, `ConversationRepository`, `ConsentRepository`, `ReminderRepository`
    - All methods return `Flow<T>` for observable queries, `suspend` for writes
    - _Requirements: 2.1–2.9_
  - [ ] 10.3 Unit tests for domain invariants
    - `Task.start <= Task.end` enforced in constructor
    - `TaskId` / `ConversationId` are UUID-shaped
    - _Requirements: 2.3_

- [ ] 11. Implement `:core:data` (Room + SQLCipher)
  - [ ] 11.1 Add `KeystoreKeyProvider`
    - Generates a per-install SQLCipher key, stored in Android Keystore (StrongBox when available) per design §3.3
    - _Requirements: 6.1, 6.2_
  - [ ] 11.2 Define Phase 1 Room entities
    - `TaskEntity`, `ConversationEntity`, `ChatMessageEntity`, `ActionPlanDraftEntity`, `ConsentDecisionEntity`, `RoutingMetricEntity` per design §3.2
    - DAOs for each
    - _Requirements: 2.1, 6.1, 9.1, 24.3, 25.6_
  - [ ] 11.3 Wire SQLCipher-backed Room database
    - `SupportFactory` with the Keystore-derived passphrase
    - Migration strategy stub (only v1 exists)
    - _Requirements: 6.1, 6.2, 6.3_
  - [ ] 11.4 Implement repository adapters
    - Concrete `TaskRepositoryImpl` etc., mapping entities ↔ domain
    - _Requirements: 2.1–2.9_
  - [ ] 11.5 Write unit tests for repository adapters
    - In-memory Room with `Room.inMemoryDatabaseBuilder` + SQLCipher bypass for JVM
    - _Requirements: 2.1_

- [ ] 12. Implement `Recurrence_Engine` (DAILY/WEEKLY only for Phase 1)
  - [ ] 12.1 Implement RRULE parser and expander for FREQ ∈ {DAILY, WEEKLY}, INTERVAL, COUNT, UNTIL
    - Pure `:core:common` module, JVM-testable
    - Function `expand(rrule, from, to): List<LocalDateTime>`
    - Full MONTHLY/YEARLY/BYDAY/BYMONTHDAY/EXDATE deferred to Phase 2
    - _Requirements: 3.1, 3.5_
  - [ ] 12.2 Write P1 (Recurrence round-trip) property test
    - **Property 1: Recurrence round-trip** — for a generated RRULE and window, expand-then-parse-then-expand yields the same occurrence list
    - **Validates:** Requirements 3.1, 3.5
    - jqwik generator covers DAILY/WEEKLY only in Phase 1 (will be expanded in Phase 2)
    - Class `RRuleRoundTripTest`, 500 iterations per design §9.2
    - _Requirements: 3.1, 3.5_
    - _Properties: P1_
  - [ ] 12.3 Unit tests for RRULE edge cases
    - COUNT=1, UNTIL in the past, INTERVAL boundary values
    - _Requirements: 3.5_

- [ ] 13. Implement `NL_Parser`
  - [ ] 13.1 Implement tokenizer, time-phrase parser, tag extractor
    - Pure `:ai:nlparser` module; no Android deps
    - Confidence scoring per design §4.2
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.6, 8.7, 8.8_
  - [ ] 13.2 Implement always-escalate pattern detector
    - Matches patterns from Req 8.9 / design §5.4 (bulk predicates, relative references, multi-action cues)
    - Returns `AlwaysEscalate` sentinel that bypasses confidence threshold
    - _Requirements: 8.9, 25.5_
  - [ ] 13.3 Write P2 (NL_Parser round-trip) property test
    - **Property 2: NL_Parser round-trip** — for a generated `TaskCandidate`, `parse(candidate.print())` yields a parse result with the same fields
    - **Validates:** Requirements 8.1–8.4
    - Class `NlParserRoundTripTest`, 500 iterations
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
    - _Properties: P2_
  - [ ] 13.4 Write P3 (NL_Parser determinism) property test
    - **Property 3: NL_Parser determinism** — calling `parse(input)` twice with the same `now`/`tz` yields byte-identical output
    - **Validates:** Requirement 8.7
    - Class `NlParserDeterminismTest`, 500 iterations
    - _Requirements: 8.7_
    - _Properties: P3_
  - [ ] 13.5 Unit tests for always-escalate patterns
    - Table-driven tests covering each pattern in Req 8.9
    - _Requirements: 8.9_

- [ ] 14. Implement `Action_Plan` JSON schema validator and GBNF generator
  - [ ] 14.1 Check in JSON schema from design §3.2 at `ai-assets/action_plan.schema.json`
    - Source of truth for both validator and GBNF per design §13
    - _Requirements: 9.1, 9.6_
  - [ ] 14.2 Implement `ActionPlanValidator`
    - Uses a JVM-compatible JSON schema validator; returns structured `ValidationError` list
    - _Requirements: 9.1, 9.6, 9.7_
  - [ ] 14.3 Implement GBNF generator script
    - Gradle task `generateGbnf` reads the JSON schema and emits `ai-assets/action_plan.gbnf`
    - Runs during `:ai:llm` build (unused until Phase 3 but wired now to avoid drift)
    - _Requirements: 9.6, 9.7_
  - [ ] 14.4 Unit tests for validator
    - Valid/invalid sample plans from design §3.2 examples
    - _Requirements: 9.1_

- [ ] 15. Implement `Task_Store` with transactional `applyPlan`
  - [ ] 15.1 Implement `Task_Store.applyPlan(plan)` as a Room transaction
    - Each action applied inside the transaction; any failure rolls the whole plan back per design §4
    - Supported action types in Phase 1: `CreateTask`, `UpdateTask`, `DeleteTask`, `CompleteTask`
    - _Requirements: 2.1, 2.2, 9.3, 9.10_
  - [ ] 15.2 Write P4 (Task time invariant) property test
    - **Property 4: Task time invariant** — after any sequence of valid operations, every persisted `Task` has `start <= end`
    - **Validates:** Requirement 2.3
    - Class `TaskTimeInvariantTest`, 200 iterations
    - _Requirements: 2.3_
    - _Properties: P4_
  - [ ] 15.3 Write P5 (Task update identity) property test
    - **Property 5: Task update identity** — updating a field to its current value is a no-op (state-equivalent)
    - **Validates:** Requirement 2.5
    - Class `TaskUpdateIdentityTest`, 200 iterations
    - _Requirements: 2.5_
    - _Properties: P5_
  - [ ] 15.4 Write P8 (Action plan apply idempotence) property test
    - **Property 8: Action plan apply idempotence** — applying the same plan twice yields the same state as applying it once (where actions carry stable target IDs)
    - **Validates:** Requirements 9.3, 9.10
    - Class `ActionPlanReplayTest`, 300 iterations
    - _Requirements: 9.3, 9.10_
    - _Properties: P8_
  - [ ] 15.5 Write P9 (Transactional rollback) property test
    - **Property 9: Transactional rollback** — injecting a failure on any single action in a valid plan leaves the DB bit-identical to the pre-apply snapshot
    - **Validates:** Requirement 9.3
    - Class `ActionPlanRollbackTest`, 300 iterations
    - _Requirements: 9.3_
    - _Properties: P9_
  - [ ] 15.6 Write P6 (Delete cancels reminders) property test — deferred scheduler portion lives in task 16
    - Storage portion covered here: deleting a Task removes its reminder rows atomically
    - **Validates:** Requirement 4.7
    - _Requirements: 4.7_
    - _Properties: P6_ (storage half)

- [ ] 16. Implement `Reminder_Scheduler` with exact alarms and BOOT_COMPLETED rescheduling
  - [ ] 16.1 Implement `AlarmManager`-based scheduler using `setExactAndAllowWhileIdle`
    - Permission `SCHEDULE_EXACT_ALARM` handled at runtime per Req 21
    - Uses `PendingIntent` with FLAG_UPDATE_CURRENT
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6, 4.8, 4.9_
  - [ ] 16.2 Implement `BootCompletedReceiver` that reschedules all pending reminders
    - Registered only for `android.intent.action.BOOT_COMPLETED` and `android.intent.action.LOCKED_BOOT_COMPLETED`
    - _Requirements: 4.9_
  - [ ] 16.3 Write P6 (Delete cancels reminders) scheduler portion
    - **Property 6: Delete cancels reminders** — after a Task delete, no pending AlarmManager intent exists for that Task
    - **Validates:** Requirement 4.7
    - Robolectric with `ShadowAlarmManager`, 200 iterations
    - _Requirements: 4.7_
    - _Properties: P6_
  - [ ] 16.4 Write P7 (Reminder cancel-after-schedule idempotence) property test
    - **Property 7: Reminder cancel-after-schedule idempotence** — arbitrary interleaving of schedule/cancel calls terminating in cancel leaves the system with no pending alarm
    - **Validates:** Requirement 4.4
    - Class `ReminderIdempotenceTest`, 200 iterations
    - _Requirements: 4.4_
    - _Properties: P7_

- [ ] 17. Implement `Notification_Service` with channels and action receivers
  - [ ] 17.1 Create notification channels
    - Channels: `reminders`, `missed_tasks` (stub for Phase 2), `habits` (stub for Phase 6), `focus` (stub for Phase 6)
    - Created in `PlannerApplication.onCreate`
    - _Requirements: 4.1, 4.2_
  - [ ] 17.2 Implement `NotificationActionReceiver` for reminder actions
    - Actions: "Mark complete", "Snooze 10 min"
    - Round-trips through `Task_Store.applyPlan`
    - _Requirements: 4.6_
  - [ ] 17.3 Wire `POST_NOTIFICATIONS` permission request for API 33+
    - Just-in-time request on first reminder schedule per design §8 Phase 1
    - _Requirements: 21.2, 21.3_

- [ ] 18. Implement `Task_Router` (parser-only in Phase 1)
  - [ ] 18.1 Implement router decision logic
    - Inputs: user text, `now`, `tz`, 7-day window context
    - Calls `NL_Parser` first; if confidence ≥ threshold AND no always-escalate match → parser route
    - Else → LLM route (Phase 1 returns `LlmUnavailable` stub)
    - Emits `RoutingMetric` per decision to `:core:data`
    - _Requirements: 25.1, 25.2, 25.4, 25.5, 25.6, 25.7_
  - [ ] 18.2 Write P23 (Router parser-first + routing invariant) property test
    - **Property 23: Router invariant** — for any input, if NL_Parser yields confidence ≥ threshold and no always-escalate pattern matches, router picks parser; otherwise it picks LLM
    - **Validates:** Requirements 25.1, 25.2, 25.5
    - Class `TaskRouterRoutingInvariantTest`, 500 iterations with `FakeNLParser`
    - _Requirements: 25.1, 25.2, 25.5_
    - _Properties: P23_

- [ ] 19. Implement `Consent_Store`
  - [ ] 19.1 Implement encrypted consent decision persistence
    - Stored in `ConsentDecisionEntity` with `(assetId, decision, decidedAt)` tuples
    - _Requirements: 24.1, 24.3_
  - [ ] 19.2 Write P17 (Consent decision round-trip) property test
    - **Property 17: Consent decision round-trip** — `store(d); fetch(assetId) == d` for any `ConsentDecision`
    - **Validates:** Requirement 24.3
    - Class `ConsentDecisionTest`, Robolectric, 500 iterations
    - _Requirements: 24.3_
    - _Properties: P17_

- [ ] 20. Implement `Timeline_View` (day view only; basic overlap layout)
  - [ ] 20.1 Implement day-view Compose screen
    - Hour-rail + task cards; scrolling; current-time indicator
    - _Requirements: 1.1, 1.2, 1.3, 1.6_
  - [ ] 20.2 Implement basic overlap layout (side-by-side for overlapping tasks)
    - Full 3+ column overlap polish deferred to Phase 2
    - _Requirements: 1.4_
  - [ ] 20.3 Write P37 (Timeline overlap layout) property test
    - **Property 37: Timeline overlap layout** — for any set of `Task` items with overlapping intervals, the rendered layout never places two cards at the same `(row, column)` and every task occupies a positive-width rectangle
    - **Validates:** Requirement 1.4
    - Class `TimelineOverlapLayoutTest`, 200 iterations, pure JVM over a layout pure-function
    - _Requirements: 1.4_
    - _Properties: P37_
  - [ ] 20.4 Instrumented UI test for Timeline rendering
    - Compose UI test verifies that a task inserted at 3 pm is visible at its slot
    - _Requirements: 1.1_

- [ ] 21. Implement Task editor screen and Task detail screen
  - [ ] 21.1 Implement `TaskEditorScreen` Compose UI
    - Fields: title, start, end, tags, priority, recurrence (DAILY/WEEKLY dropdown)
    - _Requirements: 2.1, 2.2, 2.8_
  - [ ] 21.2 Implement `TaskDetailScreen` Compose UI
    - Shows task, edit/delete/complete actions
    - _Requirements: 2.1, 2.2_

- [ ] 22. Implement `Chat_Interface` (NL path only; no LLM)
  - [ ] 22.1 Implement chat screen with conversation list and message list
    - Persists via `ConversationRepository` and `ChatMessageRepository`
    - _Requirements: 8.1, 25.1_
  - [ ] 22.2 Wire input box to `Task_Router`
    - On send: router → `NL_Parser` → `Action_Plan` builder → `Action_Plan_Review`
    - _Requirements: 8.1, 25.1, 25.2_

- [ ] 23. Implement `Action_Plan_Review` surface (single-action plans; no ambiguity picker)
  - [ ] 23.1 Implement review screen
    - Shows the plan's single action with all fields editable inline
    - Apply button → `Task_Store.applyPlan`; Cancel → dismiss
    - _Requirements: 28.1, 28.2, 28.4, 28.6, 28.7, 28.8, 28.9, 28.10, 28.11_
  - [ ] 23.2 Write P32 (Bounded action count) property test
    - **Property 32: Bounded action count** — plans submitted to review always contain between 1 and 50 actions inclusive
    - **Validates:** Requirement 27.7
    - Class `ActionPlanBoundedCountTest`, 300 iterations
    - _Requirements: 27.7_
    - _Properties: P32_
  - [ ] 23.3 Write P33 (`target_id` UUID hygiene) property test
    - **Property 33: target_id UUID hygiene** — every action's `target_id` is either null (for create) or a valid UUID that exists in the current DB snapshot
    - **Validates:** Requirement 27.6
    - Class `ActionPlanTargetIdHygieneTest`, 500 iterations
    - _Requirements: 27.6_
    - _Properties: P33_
  - [ ] 23.4 Write P34 (Review pass-through) property test
    - **Property 34: Review pass-through** — if the user does not edit, `applyPlan(reviewed_plan) == applyPlan(submitted_plan)`
    - **Validates:** Requirement 28.6
    - Class `ReviewPassThroughTest`, 200 iterations
    - _Requirements: 28.6_
    - _Properties: P34_
  - [ ] 23.5 Write P35 (Review edit fidelity) property test
    - **Property 35: Review edit fidelity** — edits made in review are reflected 1:1 in the applied plan
    - **Validates:** Requirements 28.7, 28.8
    - Class `ReviewEditFidelityTest`, 200 iterations
    - _Requirements: 28.7, 28.8_
    - _Properties: P35_
  - [ ] 23.6 Write P36 (Review cancel no-op) property test
    - **Property 36: Review cancel no-op** — cancelling in review leaves DB and scheduler state bit-identical to pre-review
    - **Validates:** Requirement 28.11
    - Class `ReviewCancelNoOpTest`, 200 iterations
    - _Requirements: 28.11_
    - _Properties: P36_

- [ ] 24. Implement onboarding flow and just-in-time permissions
  - [ ] 24.1 Implement onboarding Compose screens
    - Intro → "everything stays on device" → first task CTA
    - _Requirements: 21.1_
  - [ ] 24.2 Implement just-in-time permission requesters
    - `POST_NOTIFICATIONS` on first reminder schedule
    - `SCHEDULE_EXACT_ALARM` via `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` on first reminder schedule
    - _Requirements: 21.2, 21.3_

- [ ] 25. Write P18 (Zero network at runtime) property test with all Phase 1 operations
  - [ ] 25.1 Enumerate every Phase 1 user operation as a jqwik `@ForAll` choice
    - Create task, update task, delete task, complete task, schedule reminder, snooze reminder, chat send, apply plan, consent store, timeline query, search stub, etc.
    - _Requirements: 5.1, 5.2_
  - [ ] 25.2 Write `ZeroNetworkRuntimeTest`
    - **Property 18: Zero network at runtime** — for any random operation sequence, no socket is ever created (`NetworkDenyingSocketFactory` is never asked to produce a socket)
    - **Validates:** Requirement 5.1
    - Robolectric, 200 iterations per design §9.2
    - _Requirements: 5.1, 5.2, 5.4_
    - _Properties: P18_

- [ ] 26. Implement recurring task occurrence isolation (DAILY/WEEKLY)
  - [ ] 26.1 Implement per-occurrence override storage
    - Completing / editing a single occurrence writes a per-occurrence row that does not mutate the parent RRULE template
    - _Requirements: 3.2, 3.3, 3.4_
  - [ ] 26.2 Write P38 (Recurring isolation, DAILY/WEEKLY only) property test
    - **Property 38: Recurring occurrence isolation** — editing or completing occurrence N never changes the materialized state of any other occurrence
    - **Validates:** Requirements 3.2, 3.3
    - Class `RecurringOccurrenceIsolationTest`, 500 iterations, scope restricted to DAILY/WEEKLY in Phase 1
    - _Requirements: 3.2, 3.3_
    - _Properties: P38_

- [ ] 27. Wire all Phase 1 components in the Hilt graph
  - [ ] 27.1 Add Hilt `@Module` classes for each layer
    - `DomainModule`, `DataModule`, `SchedulingModule`, `NotificationsModule`, `AiRouterModule`, `AiParserModule`
    - LLM / speech / entity-resolver bound to no-op stubs that throw or return `Unavailable`
    - _Requirements: 25.3, 25.8_
  - [ ] 27.2 Verify DI graph at build time
    - Hilt's KSP validator must pass with no missing bindings
    - `./gradlew :app:kspDebugKotlin` green

- [ ] 28. Phase 1 manual test checklist runbook
  - [ ] 28.1 Create `docs/phase1-manual-checklist.md`
    - Covers: onboarding, create task, edit task, delete task, complete task, reminder fires, reminder action works, chat NL input round-trips, consent store round-trips
    - _Requirements: 21.1, 2.1, 4.1_

- [ ] 29. Phase 1 exit validation
  - [ ] 29.1 Run full Phase 1 PBT suite
    - P1, P2, P3, P4, P5, P6, P7, P8, P9, P17, P18, P23, P32, P33, P34, P35, P36, P37, P38, P39 all green
  - [ ] 29.2 Assemble signed release APK tagged `v0.1-mvp`
    - Delivered via Firebase App Distribution per design §8 Phase 1
    - User installs on S23 and completes the manual checklist from task 28
  - [ ]* 29.3 Run an extra ad-hoc accessibility sweep with TalkBack
    - Beyond Req 20 minimum (which lands in Phase 2); nice-to-have early sanity check

---

## PHASE 2 — Core polish

**Goal.** Non-AI features that make the app comparable to Structured. Corresponds to design §8 Phase 2.

- [ ] 30. Implement full RRULE support
  - [ ] 30.1 Extend `Recurrence_Engine` with FREQ ∈ {MONTHLY, YEARLY}
    - Plus BYDAY, BYMONTHDAY, BYSETPOS, EXDATE handling
    - _Requirements: 3.1, 3.5, 3.6_
  - [ ] 30.2 Extend P1 generator to cover the full RRULE grammar
    - **Property 1 (full scope):** Recurrence round-trip for MONTHLY/YEARLY/BYDAY/BYMONTHDAY/EXDATE/COUNT/UNTIL
    - **Validates:** Requirements 3.1, 3.5, 3.6
    - Reuses `RRuleRoundTripTest`; iteration count unchanged
    - _Requirements: 3.1, 3.5, 3.6_
    - _Properties: P1_
  - [ ] 30.3 Extend P38 generator to cover full RRULE
    - **Property 38 (full scope):** Recurring occurrence isolation under full RRULE grammar
    - **Validates:** Requirements 3.2, 3.3
    - _Requirements: 3.2, 3.3_
    - _Properties: P38_

- [ ] 31. Implement Week view
  - [ ] 31.1 Implement `WeekTimelineScreen`
    - 7-column grid + hour-rail on the side
    - _Requirements: 1.7_
  - [ ] 31.2 Compose UI test for week rendering
    - _Requirements: 1.7_

- [ ] 32. Implement horizontal swipe navigation between days
  - [ ] 32.1 Add `HorizontalPager` over day-view screens
    - _Requirements: 1.5_

- [ ] 33. Implement full overlap side-by-side layout
  - [ ] 33.1 Extend the overlap algorithm to N columns with column packing
    - _Requirements: 1.4_
  - [ ] 33.2 Extend P37 generator to cover 3+ overlap cases
    - **Property 37 (full scope):** Timeline overlap layout correctness for arbitrary overlap density
    - **Validates:** Requirement 1.4
    - _Requirements: 1.4_
    - _Properties: P37_

- [ ] 34. Implement missed-task scanner Worker
  - [ ] 34.1 Implement `MissedTaskScannerWorker`
    - Runs periodically via WorkManager; generates `missed_tasks` channel notifications for past-due uncompleted tasks
    - _Requirements: 4.5_
  - [ ] 34.2 Robolectric test for Worker scheduling shape
    - _Requirements: 4.5_

- [ ] 35. Implement FTS5 `Search_Index`
  - [ ] 35.1 Create Room FTS5 virtual table for tasks
    - Content table = `TaskEntity`; triggers to keep FTS in sync
    - _Requirements: 17.1, 17.2, 17.3_
  - [ ] 35.2 Implement `SearchRepository` with case-insensitive prefix + token queries
    - _Requirements: 17.1, 17.2, 17.3_
  - [ ] 35.3 Implement `SearchScreen` Compose UI
    - _Requirements: 17.1_
  - [ ] 35.4 Write P12 (Search subset) property test
    - **Property 12: Search subset** — search results are always a subset of the task universe matching the query
    - **Validates:** Requirement 17.1
    - Class `SearchSubsetTest`, 500 iterations (JVM reference impl + Robolectric against real FTS5)
    - _Requirements: 17.1_
    - _Properties: P12_
  - [ ] 35.5 Write P13 (Search empty query) property test
    - **Property 13: Search empty query** — empty query returns empty results
    - **Validates:** Requirement 17.2
    - Class `SearchEmptyQueryTest`, 200 iterations
    - _Requirements: 17.2_
    - _Properties: P13_
  - [ ] 35.6 Write P14 (Search CRUD reflects) property test
    - **Property 14: Search CRUD reflects** — for any sequence of CRUD ops, subsequent search reflects the final state
    - **Validates:** Requirement 17.3
    - Class `SearchCrudReflectsTest`, 300 iterations, Robolectric with real Room FTS5
    - _Requirements: 17.3_
    - _Properties: P14_

- [ ] 36. Implement Quick_Capture widget + Sharesheet
  - [ ] 36.1 Implement Glance widget for `Quick_Capture`
    - One-tap opens the Quick_Capture surface; long-press variants for "Add now"
    - _Requirements: 15.1, 18.1, 18.2_
  - [ ] 36.2 Register Sharesheet intent filter for `ACTION_SEND` text
    - Incoming text routed through `Task_Router` → `Action_Plan_Review`
    - _Requirements: 15.2, 18.1_
  - [ ] 36.3 Robolectric/instrumented tests for widget + share-receive
    - _Requirements: 15.1, 15.2, 18.1, 18.2_

- [ ] 37. Accessibility polish
  - [ ] 37.1 Add content descriptions to every Compose `Image`/`Icon`
    - _Requirements: 20.1, 20.2_
  - [ ] 37.2 Ensure all tap targets are ≥ 48dp
    - _Requirements: 20.3_
  - [ ] 37.3 Verify screen reader navigation order across all Phase 1+2 screens
    - Manual TalkBack sweep + automated `UiAutomator` test where feasible
    - _Requirements: 20.4_
  - [ ] 37.4 Dynamic font scaling support
    - All text uses `sp`; layouts reflow under 200% font scale
    - _Requirements: 20.5_
  - [ ]* 37.5 Extra accessibility audit beyond Req 20
    - Test with TalkBack + BrailleBack + Switch Access; document any findings
    - Optional — goes beyond the specified Req 20 acceptance criteria

- [ ] 38. Material You completion
  - [ ] 38.1 Full Material You dynamic color wiring on API 31+
    - Settings toggle for "Use system colors"
    - _Requirements: 22.2_
  - [ ]* 38.2 Additional theme variants beyond light/dark/system
    - High-contrast, sepia, custom accent color palette
    - Optional — goes beyond the specified Req 22 acceptance criteria

- [ ] 39. Macrobenchmark module setup
  - [ ] 39.1 Create `:benchmark` module
    - `androidx.benchmark:benchmark-macro-junit4`
    - _Requirements: 23.1, 23.3_
  - [ ] 39.2 Add cold-start benchmark
    - Asserts cold-start P95 under the budget in Req 23.1
    - _Requirements: 23.1_
  - [ ] 39.3 Add Timeline render benchmark
    - Frame timing during scroll; asserts against Req 23.3 budget
    - _Requirements: 23.3_

- [ ] 40. Phase 2 exit validation
  - [ ] 40.1 Run full Phase 1+2 PBT suite
    - All Phase 1 properties still green + P1 (full scope), P12, P13, P14, P37 (full scope), P38 (full scope)
  - [ ] 40.2 Assemble signed release APK tagged `v0.2-polish`
    - Delivered via Firebase App Distribution per the hybrid decision (§12.1) — APK still well under 150 MB, no PAD yet

---

## PHASE 3 — LLM Chat MVP

**Goal.** First real on-device LLM with bundled Gemma 3 1B, GBNF grammar, deterministic few-shot, conversational fallback. **This phase introduces Play Asset Delivery install-time asset packs AND switches primary distribution to Play Internal Testing**, per the hybrid decision locked in design §12.1. Corresponds to design §8 Phase 3.

- [ ] 41. Create `:distribution:model-downloader` module (INTERNET-only module)
  - [ ] 41.1 Declare the module as a Play Feature Delivery dynamic feature
    - `dist:module dist:delivery` with `install-time` for Phase 3 (Phase 4 will add `on-demand` variants)
    - _Requirements: 5.5, 5.8, 24.1_
  - [ ] 41.2 Add the `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` permissions to this module's manifest
    - This is the ONLY module where those permissions may appear per design §5.3
    - `checkManifests` task allowlist updated to recognize this module
    - _Requirements: 5.8_
  - [ ] 41.3 Implement `ConsentedDownloader` API that downloads model assets within `withConsentedDownload { ... }`
    - Body used in Phase 4 for recommended/optional model downloads; Phase 3 wires it but does not invoke it (Gemma 3 1B ships via PAD, not downloader)
    - _Requirements: 24.1, 24.2, 24.4_

- [ ] 42. Play Asset Delivery install-time asset pack for Gemma 3 1B
  - [ ] 42.1 Create Gradle asset pack module `gemma3_1b_q4_km`
    - `android.assetPack { packName = "gemma3_1b_q4_km"; dynamicDelivery { deliveryType = "install-time" } }` per design §12.3
    - Contents: Gemma 3 1B Q4_K_M `.task` bundle
    - _Requirements: 7.6, 7.18_
  - [ ] 42.2 Implement `AssetPackAccessor` that resolves the pack's install path
    - Uses `AssetPackManager.getPackLocation`; reports `PENDING` to UI as "Preparing models…" (design §12.5)
    - _Requirements: 7.18_
  - [ ] 42.3 Write unit test for asset pack path resolution
    - Fake `AssetPackManager` returns `PENDING` / `COMPLETED`; accessor behaves correctly
    - _Requirements: 7.18_

- [ ] 43. Implement MediaPipe LLM Inference adapter (`:ai:llm`)
  - [ ] 43.1 Add MediaPipe GenAI dependency for `.task` bundles
    - _Requirements: 7.1, 7.2, 7.9_
  - [ ] 43.2 Implement `MediaPipeLlmAdapter : LlmAdapter`
    - Load / unload / invoke with grammar
    - Throws `LlmUnavailable` if MediaPipe's grammar support is absent for Gemma 3 1B at runtime (design §12.2)
    - _Requirements: 7.1, 7.2, 7.9, 7.12, 9.6_

- [ ] 44. Implement llama.cpp Android JNI adapter (`:ai:llm`)
  - [ ] 44.1 Vendor llama.cpp via Prefab or git submodule with CMake
    - Builds `libllama.so` for arm64-v8a, x86_64
    - _Requirements: 7.3, 7.4_
  - [ ] 44.2 Implement `LlamaCppLlmAdapter : LlmAdapter`
    - Load / unload / invoke with GBNF grammar
    - _Requirements: 7.3, 7.4, 7.12, 9.6, 9.7_
  - [ ] 44.3 Implement adapter-selection policy
    - MediaPipe preferred when model format = `.task` and grammar supported; llama.cpp fallback otherwise (design §12.2)
    - _Requirements: 7.2, 7.3_

- [ ] 45. Wire GBNF grammar compilation into `:ai:llm`
  - [ ] 45.1 Ensure Gradle task `generateGbnf` runs before `:ai:llm` compilation
    - Generated grammar is loaded at runtime by both adapters
    - _Requirements: 9.6, 9.7_
  - [ ] 45.2 Write a build-time test that fails if generated GBNF diverges from schema
    - Compares hash of regenerated GBNF against checked-in copy; CI fails on mismatch
    - _Requirements: 9.6_

- [ ] 46. Implement `Few_Shot_Examples` loader and deterministic similarity selector
  - [ ] 46.1 Check in `ai-assets/few_shot_examples.json`
    - Initial set per design §4.4; each entry has `input`, `plan`, `signature`
    - _Requirements: 26.1, 26.2, 26.3_
  - [ ] 46.2 Implement similarity selector
    - Deterministic cosine-similarity-over-token-bag scorer; ties broken by stable sort on example ID
    - _Requirements: 26.2, 26.6_
  - [ ] 46.3 Write P24 (Few-shot selection determinism) property test
    - **Property 24: Few-shot selection determinism** — same input + same DB subset → same example ordering
    - **Validates:** Requirement 26.6
    - Class `FewShotSelectionDeterminismTest`, 500 iterations
    - _Requirements: 26.6_
    - _Properties: P24_
  - [ ] 46.4 Write P25 (Few-shot replay) property test
    - **Property 25: Few-shot replay** — each `(input, plan)` entry in the examples file, when fed to the full router → LLM → validator pipeline with the FakeLlm, reproduces `plan`
    - **Validates:** Requirements 26.1, 26.3
    - Class `FewShotReplayTest`, iterates every entry; separate `@LargeTest` real-model variant runs on FTL per design §9.2
    - _Requirements: 26.1, 26.3_
    - _Properties: P25_

- [ ] 47. Implement `LLM_Agent` lazy lifecycle
  - [ ] 47.1 Implement load / unload / idle timer state machine
    - Loads on first use; unloads after `idleTimeoutMs` (per design §4.5); never loads while screen off unless user explicitly triggered
    - _Requirements: 7.10, 7.13, 7.14, 7.20_
  - [ ] 47.2 Write P19 (LLM lifecycle) property test
    - **Property 19: LLM lifecycle** — for any event timeline with `FakeClock` and `FakeLlm`, the agent is loaded iff there is an active request or the idle timer has not expired
    - **Validates:** Requirements 7.13, 7.14
    - Class `LlmLifecycleTest`, 300 iterations
    - _Requirements: 7.13, 7.14_
    - _Properties: P19_

- [ ] 48. Implement concurrency rejection
  - [ ] 48.1 `LLM_Agent` rejects a second concurrent request with `Busy`
    - Single-flight mutex per design §4.5
    - _Requirements: 7.15_
  - [ ] 48.2 Write P21 (Concurrency rejection) property test
    - **Property 21: Concurrency rejection** — for any pair of overlapping submissions, exactly one is accepted and the other returns `Busy` without mutating state
    - **Validates:** Requirement 7.15
    - Class `LlmConcurrencyTest`, 200 iterations with `TestCoroutineScheduler`
    - _Requirements: 7.15_
    - _Properties: P21_

- [ ] 49. Implement screen-off gating
  - [ ] 49.1 `LLM_Agent` refuses to start inference when screen is off unless user explicitly initiated
    - Listens to `Intent.ACTION_SCREEN_OFF`/`ON` via `PowerManager`
    - _Requirements: 7.20_
  - [ ] 49.2 Write P20 (Screen-off gating) property test
    - **Property 20: Screen-off gating** — with arbitrary screen on/off timelines and submissions, inference starts iff screen is on OR the submission carries an explicit-action flag
    - **Validates:** Requirement 7.20
    - Class `ScreenOffGatingTest`, 200 iterations
    - _Requirements: 7.20_
    - _Properties: P20_

- [ ] 50. Implement context inclusion
  - [ ] 50.1 Assemble `RouterContext` with `{now, tz, 7-day-window tasks}` for Phase 3
    - Passed to LLM on every invocation; `FakeLlm` captures
    - _Requirements: 7.17, 7.18_
  - [ ] 50.2 Write P22 (Context inclusion) property test
    - **Property 22: Context inclusion** — every LLM invocation carries the required context fields
    - **Validates:** Requirements 7.17, 7.18
    - Class `LlmContextInclusionTest`, 200 iterations
    - _Requirements: 7.17, 7.18_
    - _Properties: P22_

- [ ] 51. Implement grammar-constrained output + fallback
  - [ ] 51.1 Wire GBNF into both adapters
    - Any invocation includes the GBNF grammar; invalid output is retried once; second failure → conversational fallback message
    - _Requirements: 9.6, 9.7, 9.8, 9.9_
  - [ ] 51.2 Write P26 (Grammar-constrained output) property test
    - **Property 26: Grammar-constrained output** — for arbitrary token streams from FakeLlm, the grammar filter always produces either a valid `ActionPlan` (schema-validated) or the documented fallback message
    - **Validates:** Requirements 9.6, 9.7, 9.8, 9.9
    - Class `GrammarConstrainedOutputTest`, 1000 iterations
    - _Requirements: 9.6, 9.7, 9.8, 9.9_
    - _Properties: P26_

- [ ] 52. Chat persistence
  - [ ] 52.1 Implement per-conversation pagination in `ChatMessageRepository`
    - Keyset-pagination; most-recent-first; 50 messages per page
    - _Requirements: 8.1, 25.1_
  - [ ] 52.2 Unit tests for pagination correctness
    - _Requirements: 8.1_

- [ ] 53. Power mode settings
  - [ ] 53.1 Implement `Settings → Power mode` screen
    - Options: Balanced, Battery Saver, Performance; maps to LLM adapter tuning knobs per design §4.5
    - _Requirements: 7.21, 7.22_

- [ ] 54. Diagnostics screen
  - [ ] 54.1 Implement `Settings → Diagnostics` Compose screen
    - Shows: LLM cpuMsCounter, router tier mix (parser vs LLM), consent decisions list, storage usage per asset pack, last 10 crash signatures (all local)
    - _Requirements: 7.19, 5.7, 7.24_

- [ ] 55. Switch primary distribution to Play Internal Testing
  - [ ] 55.1 Configure Play Console Internal Testing track
    - Set up the app in Play Console; upload the Phase 3 AAB; add tester emails including the user
    - _Requirements: 7.6, 24.1_
  - [ ] 55.2 Update CI pipeline
    - New stage `uploadToPlayInternalTesting` (uses `gradle-play-publisher` or `fastlane supply`) — runs in addition to `uploadToFirebaseAppDistribution` so the secondary channel remains available per the hybrid decision (§12.1)
    - _Requirements: (distribution — no AC number)_
  - [ ] 55.3 Document distribution-channel switchover
    - `docs/install.md` updated: Play Internal Testing is now the recommended primary install path; Firebase App Distribution remains available as a fallback fat-APK channel

- [ ] 56. Phase 3 exit validation
  - [ ] 56.1 Run full Phase 1+2+3 PBT suite
    - P19, P20, P21, P22, P24, P25, P26 green; all earlier properties still green
  - [ ] 56.2 Assemble signed release AAB tagged `v0.3-llm-mvp`
    - Primary: Play Internal Testing with `gemma3_1b_q4_km` install-time PAD pack
    - Secondary: Firebase App Distribution fat APK (assets bundled inside the APK, < 2 GB)
  - [ ]* 56.3 Manual smoke test of the LLM conversational fallback path
    - Optional manual sanity check beyond the automated P26 coverage

---

## PHASE 4 — Compositional Chat

**Goal.** Multi-action plans, bulk predicates, relative references, ambiguity handling. Corresponds to design §8 Phase 4.

- [ ] 57. Implement `Entity_Resolver` with bulk predicate rule table
  - [ ] 57.1 Implement bulk predicate resolver
    - Rule table per design §4.3.2 covers predicates like "all overdue", "everything tagged X", "tasks this week"
    - _Requirements: 27.2_
  - [ ] 57.2 Write P27 (Entity_Resolver bulk) property test
    - **Property 27: Entity_Resolver bulk expansion** — bulk predicate expansion is a deterministic function of `(dbSnapshot, predicate)` and matches a declarative spec of the predicate
    - **Validates:** Requirement 27.2
    - Class `EntityResolverBulkExpansionTest`, 500 iterations
    - _Requirements: 27.2_
    - _Properties: P27_

- [ ] 58. Implement reference resolution
  - [ ] 58.1 Implement reference-priority rules
    - Per design §4.3.3: explicit UUID > recent-context reference > ordinal reference > fuzzy title match > ambiguous
    - _Requirements: 27.3_
  - [ ] 58.2 Write P28 (Entity_Resolver reference priority) property test
    - **Property 28: Entity_Resolver reference priority** — given a DB and a reference string, the resolver picks the highest-priority matching entity
    - **Validates:** Requirement 27.3
    - Class `EntityResolverReferencePriorityTest`, 500 iterations
    - _Requirements: 27.3_
    - _Properties: P28_

- [ ] 59. Implement relative-temporal rules
  - [ ] 59.1 Implement relative-temporal resolution
    - "tomorrow", "next week", "in 3 days" resolved against `now`, `tz`
    - _Requirements: 27.4_
  - [ ] 59.2 Write P29 (Relative-temporal rules) property test
    - **Property 29: Relative-temporal determinism** — same `(input, now, tz)` → same resolved instant
    - **Validates:** Requirement 27.4
    - Class `RelativeTemporalDeterminismTest`, 500 iterations
    - _Requirements: 27.4_
    - _Properties: P29_

- [ ] 60. Entity_Resolver determinism
  - [ ] 60.1 Ensure whole `Entity_Resolver` is pure and deterministic
    - No randomness, no system-time side effects beyond explicit `now` parameter
    - _Requirements: 27.5_
  - [ ] 60.2 Write P30 (Entity_Resolver determinism) property test
    - **Property 30: Entity_Resolver determinism** — `resolve(input, db, now, tz)` twice yields byte-identical output
    - **Validates:** Requirement 27.5
    - Class `EntityResolverDeterminismTest`, 500 iterations
    - _Requirements: 27.5_
    - _Properties: P30_

- [ ] 61. Multi-action plan support
  - [ ] 61.1 Extend `Action_Plan` schema and validator to enforce 1 ≤ N ≤ 50 actions
    - _Requirements: 27.7, 27.8_
  - [ ] 61.2 Extend `Action_Plan_Review` to render multi-action plans
    - List view with per-action inline edit; add/remove/reorder controls (task 63)
    - _Requirements: 28.7, 28.8, 28.10_
  - [ ] 61.3 Extend P32 scope
    - **Property 32 (compositional scope):** plans produced by LLM path also obey the 1..50 bound
    - **Validates:** Requirement 27.7
    - _Requirements: 27.7_
    - _Properties: P32_

- [ ] 62. UUID hygiene for compositional plans
  - [ ] 62.1 Extend P33 scope to LLM-produced plans
    - **Property 33 (compositional scope):** `target_id` hygiene holds across all LLM-produced plans after `Entity_Resolver` fills in UUIDs
    - **Validates:** Requirements 27.6, 27.8
    - _Requirements: 27.6, 27.8_
    - _Properties: P33_

- [ ] 63. Ambiguity marker + picker UI
  - [ ] 63.1 Implement `Ambiguity_Marker` types in `Action_Plan`
    - Marker variants: `AmbiguousEntity(candidates)`, `AmbiguousTime(candidates)`, `AmbiguousTag(candidates)` per design §4.3.4
    - _Requirements: 27.9, 28.3_
  - [ ] 63.2 Implement picker UI in `Action_Plan_Review`
    - Inline picker chip per marker; until resolved, Apply is disabled
    - _Requirements: 28.3, 28.13_
  - [ ] 63.3 Write P31 (Ambiguity gating) property test
    - **Property 31: Ambiguity gating** — a plan with any unresolved `Ambiguity_Marker` cannot be applied; resolving all markers unlocks Apply
    - **Validates:** Requirements 27.9, 28.3
    - Class `AmbiguityGatingTest`, 500 iterations
    - _Requirements: 27.9, 28.3_
    - _Properties: P31_

- [ ] 64. Natural-language affirmative reply
  - [ ] 64.1 Implement affirmative detection in `Chat_Interface`
    - Patterns: "go ahead", "submit", "yes do it", etc.
    - Triggers apply on the currently-open review
    - _Requirements: 28.12_

- [ ] 65. Add / remove / reorder actions in review
  - [ ] 65.1 Implement action manipulation controls
    - `+` to add a hand-crafted action; swipe-to-delete; drag-handle reorder
    - _Requirements: 28.8, 28.10_
  - [ ] 65.2 Instrumented UI tests for manipulation
    - _Requirements: 28.8, 28.10_

- [ ] 66. Always-escalate full coverage
  - [ ] 66.1 Extend P23 generator to cover the complete always-escalate set
    - **Property 23 (full scope):** router + NL_Parser + always-escalate patterns route correctly for the whole Req 8.9 set
    - **Validates:** Requirements 25.1, 25.2, 25.5, 8.9
    - _Requirements: 8.9, 25.1, 25.2, 25.5_
    - _Properties: P23_

- [ ] 67. Recommended-model consent + download
  - [ ] 67.1 Implement Gemma 2 2B download via `ConsentedDownloader`
    - Download only within `withConsentedDownload`; progress surfaced in `Settings → Models`
    - _Requirements: 7.6, 7.7, 24.1, 24.2, 24.4_
  - [ ] 67.2 Persist download state
    - `ModelAssetEntity` row per model with `{installed, integrityHashOk, bytes}`
    - _Requirements: 24.3_
  - [ ] 67.3 Unit tests for integrity hash verification
    - SHA-256 expected value checked into a manifest; mismatch rejects the asset
    - _Requirements: 7.11_

- [ ] 68. Optional-model downloads
  - [ ] 68.1 Wire Phi-3.5 Mini, Gemma 3 4B, Llama 3.2 3B, Qwen 2.5 3B as optional downloads
    - Each shown in `Settings → Models` with memory-budget warning where peak RSS > 3 GB (design §12.2)
    - _Requirements: 7.8, 24.1, 24.4_
  - [ ]* 68.2 LoRA adapter bundling (Req 26.4 MAY)
    - Optional per Req 26.4; implement the loader if time permits

- [ ] 69. `Network_Consent_Dialog` full implementation
  - [ ] 69.1 Implement consent dialog with full "grant / deny / revoke / delete asset" flow
    - Revoke → future downloads denied; Delete asset → removes the file and its `ModelAssetEntity` row
    - _Requirements: 24.1, 24.2, 24.4, 24.5_

- [ ] 70. `Settings → Models` surface
  - [ ] 70.1 Implement Compose screen
    - Lists bundled + optional models, their state, size, download/delete controls
    - _Requirements: 24.4_

- [ ] 71. Phase 4 exit validation
  - [ ] 71.1 Run full PBT suite
    - P23 (full scope), P27, P28, P29, P30, P31, P32 (compositional scope), P33 (compositional scope) green; all earlier green
  - [ ] 71.2 Assemble signed release AAB tagged `v0.4-compositional`
    - Delivered via Play Internal Testing (primary) + Firebase App Distribution fat APK (secondary) per §12.1

---

## PHASE 5 — Speech

**Goal.** Voice capture in `Chat_Interface` and `Quick_Capture`. Corresponds to design §8 Phase 5. **Introduces a second PAD install-time asset pack (whisper-tiny).**

- [ ] 72. Implement whisper.cpp JNI wrapper
  - [ ] 72.1 Vendor whisper.cpp via CMake submodule
    - Builds `libwhisper.so` for arm64-v8a, x86_64
    - _Requirements: 10.1, 10.2_
  - [ ] 72.2 Implement `WhisperJni` with `init`, `transcribe`, `release`
    - _Requirements: 10.1, 10.2_

- [ ] 73. Implement `WhisperTinyImpl` (default offline speech engine)
  - [ ] 73.1 Implement `SpeechRecognizer` interface + `WhisperTinyImpl`
    - Reads PCM 16-bit mono 16 kHz from `AudioRecord`; feeds whisper-tiny
    - _Requirements: 10.1, 10.2, 10.3_

- [ ] 74. Implement `AndroidSpeechRecognizerImpl` with offline verification
  - [ ] 74.1 Wrap `android.speech.SpeechRecognizer` for devices/locales that support offline recognition
    - Uses `RecognizerIntent.EXTRA_PREFER_OFFLINE` + runtime check against `ACTION_RECOGNIZE_SPEECH` extras per design §4.7
    - Rejects if OS returns any non-offline engine
    - _Requirements: 10.4, 10.5_

- [ ] 75. Implement `Speech_Recognizer` factory
  - [ ] 75.1 Factory chooses between `WhisperTinyImpl` (default) and `AndroidSpeechRecognizerImpl` (user opt-in)
    - _Requirements: 10.3, 10.4_

- [ ] 76. Microphone permission flow
  - [ ] 76.1 Implement just-in-time `RECORD_AUDIO` permission request
    - First mic tap triggers request; rationale shown on prior denial
    - _Requirements: 21.4_

- [ ] 77. Bundle whisper-tiny via PAD install-time asset pack
  - [ ] 77.1 Create Gradle asset pack module `whisper_tiny`
    - `android.assetPack { packName = "whisper_tiny"; dynamicDelivery { deliveryType = "install-time" } }`
    - _Requirements: 10.1, 10.6_
  - [ ] 77.2 `AssetPackAccessor` extended to resolve `whisper_tiny` path
    - "Preparing models…" UI also handles this pack in the `PENDING` state
    - _Requirements: 10.6_

- [ ] 78. Optional whisper-base / whisper-small downloads
  - [ ] 78.1 Wire `whisper-base` and `whisper-small` as optional consented downloads via `:distribution:model-downloader`
    - Integrity hash verification per task 67
    - _Requirements: 10.7, 24.1_

- [ ] 79. Integrate speech into `Chat_Interface` and `Quick_Capture`
  - [ ] 79.1 Add mic button to chat input and to `Quick_Capture` surface
    - Transcribed text is routed through `Task_Router`
    - _Requirements: 10.1, 15.1, 15.2_

- [ ] 80. Phase 5 exit validation
  - [ ] 80.1 Run full PBT suite
    - All earlier properties green; no new PBTs required for speech (speech verification is example-based per design §9)
  - [ ] 80.2 Assemble signed release AAB tagged `v0.5-speech`
    - Delivered via Play Internal Testing (primary) with whisper_tiny + gemma3_1b_q4_km install-time PAD packs; Firebase App Distribution fat APK as optional secondary
  - [ ]* 80.3 Performance mode with aggressive whisper tuning
    - Optional — goes beyond specified tuning; uses larger decoding beams on high-RAM devices

---

## PHASE 6 — Productivity Layer

**Goal.** Habits, Focus Sessions, Planning and Shutdown Rituals, ICS Importer, Backup/Restore. Corresponds to design §8 Phase 6.

- [ ] 81. Implement `Habit_Tracker` + streak math
  - [ ] 81.1 Implement `HabitEntity`, `HabitCompletionEntity`, and `HabitRepository`
    - _Requirements: 11.1, 11.2_
  - [ ] 81.2 Implement streak math (current streak, longest streak, percentage complete)
    - Pure `:core:common` function; JVM-testable
    - _Requirements: 11.3_
  - [ ] 81.3 Implement habit list Compose UI + completion toggle
    - _Requirements: 11.1, 11.2_
  - [ ] 81.4 Write P10 (Streak invariant) property test
    - **Property 10: Streak invariant** — for any habit with any completion history, current-streak ≤ longest-streak and both match the declarative spec
    - **Validates:** Requirement 11.3
    - Class `StreakInvariantTest`, 500 iterations
    - _Requirements: 11.3_
    - _Properties: P10_
  - [ ] 81.5 Habit reminder notifications
    - Uses `reminders` channel + per-habit schedule
    - _Requirements: 11.4_

- [ ] 82. Implement `Focus_Session_Manager` + foreground service
  - [ ] 82.1 Implement `FocusService` (foreground service with ongoing notification)
    - Per design §4.6; `FOREGROUND_SERVICE_TYPE_DATA_SYNC` not applicable — use `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` or equivalent
    - _Requirements: 12.1, 12.2, 12.3_
  - [ ] 82.2 Implement session persistence every 30 s (design §7 table)
    - Survives system-kill: on re-open, snackbar + offer to resume
    - _Requirements: 12.4_
  - [ ] 82.3 Implement focus session Compose UI
    - Start / pause / resume / end controls; timer
    - _Requirements: 12.1, 12.2_
  - [ ] 82.4 Write P11 (Focus session time invariant) property test
    - **Property 11: Focus session time invariant** — for any work/pause/resume event sequence, `elapsedWork + elapsedPause == now - start`
    - **Validates:** Requirement 12.3
    - Class `FocusSessionTimeInvariantTest`, 500 iterations
    - _Requirements: 12.3_
    - _Properties: P11_

- [ ] 83. Implement `Planning_Ritual` and `Shutdown_Ritual`
  - [ ] 83.1 Implement planning ritual workflow
    - Morning prompt: "Plan today"; walks the user through carry-overs, top 3, calendar review
    - _Requirements: 13.1, 13.2_
  - [ ] 83.2 Implement shutdown ritual workflow
    - Evening prompt: "Close the day"; reviews completed, re-schedules overflow, journals
    - _Requirements: 13.3, 13.4_
  - [ ] 83.3 Planning / shutdown ritual reminders
    - User-configurable daily times
    - _Requirements: 13.5_

- [ ] 84. Implement `ICS_Importer`
  - [ ] 84.1 Implement full RFC 5545 VEVENT parser
    - Handles SUMMARY, DTSTART, DTEND/DURATION, RRULE, EXDATE, TZID, UID
    - _Requirements: 16.1, 16.2, 16.3_
  - [ ] 84.2 Implement import UI
    - Pick `.ics` via `ACTION_OPEN_DOCUMENT`; preview before import; dedupe by UID
    - _Requirements: 16.1, 16.4_
  - [ ] 84.3 Write P15 (ICS round-trip) property test
    - **Property 15: ICS round-trip** — generated `VEVENT` → parse → re-serialize yields equivalent `VEVENT` (semantic equality, not byte equality)
    - **Validates:** Requirements 16.1, 16.2
    - Class `IcsRoundTripTest`, 500 iterations
    - _Requirements: 16.1, 16.2_
    - _Properties: P15_

- [ ] 85. Implement `Backup_Manager`
  - [ ] 85.1 Implement export
    - Serializes the whole DB snapshot + consent decisions; wraps with Argon2id-derived key encryption per design §3.3
    - Writes `.planner-backup` file via `ACTION_CREATE_DOCUMENT`
    - _Requirements: 19.1, 19.2, 19.3_
  - [ ] 85.2 Implement import
    - Reads `.planner-backup`; prompts for passphrase; Argon2id-derives key; decrypts; replaces DB
    - _Requirements: 19.1, 19.4_
  - [ ] 85.3 Write P16 (Backup round-trip) property test
    - **Property 16: Backup round-trip** — for any `LocalDbState` and any passphrase, `import(export(state, pw), pw) == state`
    - **Validates:** Requirements 19.1, 19.2, 19.3, 19.4
    - Class `BackupRoundTripTest`, 200 iterations; serializer tested in isolation on JVM, full encrypted-file round-trip on emulator (design §9.2)
    - _Requirements: 19.1, 19.2, 19.3, 19.4_
    - _Properties: P16_

- [ ] 86. Phase 6 exit validation
  - [ ] 86.1 Run full PBT suite
    - P10, P11, P15, P16 green; all earlier properties still green
  - [ ] 86.2 Assemble signed release AAB tagged `v0.6-productivity` → `v1.0-ga`
    - Delivered via Play Internal Testing (primary) + Firebase App Distribution fat APK (secondary) per §12.1
  - [ ]* 86.3 Wear OS companion surface
    - Optional — Wear OS is not in the spec's acceptance criteria; can ship later as a separate effort

---

## PHASE 7 — Public Play Store Release (conditional)

**Goal.** Publish Aetheris Planner to the Google Play Production track. **This phase is conditional on the user having used `v1.0-ga` from Phase 6 on their own device for ≥ 2 weeks AND having decided to proceed with a public release.** Do NOT execute this phase until that precondition is explicitly confirmed. Corresponds to design §8 Phase 7 and §12.1.B1.

- [ ] 87. Create Google Play developer account (Personal)
  - [ ] 87.1 Register a Personal Google Play developer account
    - Pay the one-time $25 registration fee
    - Complete identity verification
    - Accept the Play Developer Distribution Agreement
    - _Requirements: 29.9_

- [ ] 88. Enroll the app in Google Play App Signing
  - [ ] 88.1 Upload the existing release keystore as the upload key
    - Upload-key alias: `aetheris-release` (same alias used since Phase 0; see design §2.1 / §9.4)
    - Enable Play App Signing so Google Play holds the final app signing key
    - Record the app signing SHA-256 fingerprint returned by the Play Console for future reference (e.g. integrations that pin the signing certificate)
    - _Requirements: 29.12_

- [ ] 89. Prepare Play Console store listing assets
  - [ ] 89.1 Produce graphics and marketing copy
    - App icon: adaptive launcher icon (already exists from Phase 0) + 512×512 Play icon
    - Feature graphic: 1024×500
    - Phone screenshots: at least 2 (recommended 4–8)
    - 7" tablet screenshots and 10" tablet screenshots
    - Short description (≤ 80 chars) using the tagline "Offline AI Planner for your Daily Life"
    - Full description (≤ 4000 chars) describing the offline-first product, on-device LLM, and privacy stance
    - Categorization: Productivity
    - Display name in the store: "Aetheris Planner"
    - _Requirements: 29.1, 29.9, 29.11_

- [ ] 90. Complete the Play Console Data Safety form
  - [ ] 90.1 Declare no data collection, no data sharing, no off-device transmission
    - Matches Req 5 runtime behavior and Property 18 (Zero network at runtime)
    - _Requirements: 29.10_

- [ ] 91. Complete the Play Console content rating questionnaire
  - [ ] 91.1 Answer the IARC content rating questionnaire and submit
    - _Requirements: 29.13_

- [ ] 92. Link the privacy policy URL in the Play Console listing
  - [ ] 92.1 Enter the GitHub Pages URL from task 8.6 in the listing's Privacy Policy field
    - Also confirm the in-app Settings → About screen links to the same URL
    - _Requirements: 29.11_

- [ ] 93. Set up Play Console Closed Testing track with ≥ 20 testers for ≥ 14 days
  - [ ] 93.1 Configure a Closed Testing track
    - Upload the `v1.0-ga` AAB produced in Phase 6
    - Recruit ≥ 20 testers; the testing window must last ≥ 14 continuous days
    - This is Google Play's prerequisite for new Personal developer accounts before the first Production release
    - _Requirements: 29.13_

- [ ] 94. Configure staged rollout for Production
  - [ ] 94.1 Configure the production track with a staged rollout plan
    - Initial rollout: 10%
    - Monitor installs and crashes in Play Console for 72 hours
    - Expand to 25%, then 50%, then 100%, using the same 72-hour monitoring gate at each step
    - Halt rollout on any material regression until the cause is understood
    - _Requirements: 29.9_

- [ ] 95. Publish v1.0 to Production
  - [ ] 95.1 Promote the `v1.0-ga` release from Phase 6 to the Production track
    - Proceeds only after tasks 93 (Closed Testing prerequisite) and 94 (staged rollout configured) are complete
    - _Requirements: 29.9, 29.13_

- [ ]* 96. Purchase and configure the `aetheris.dev` or `aetheris.app` domain
  - [ ]* 96.1 Register the domain
    - OPTIONAL: cosmetic. Enables a possible future `com.aetheris.planner` rebrand, professional email, and a standalone marketing page. Not required for the release.

- [ ] 97. Phase 7 exit validation
  - [ ] 97.1 Verify the Play Store listing is live at 100% rollout and publicly installable
    - _Requirements: 29.9_
  - [ ] 97.2 Verify the public GitHub repository state
    - `aetheris` is public; `LICENSE` and `NOTICE` present at repo root; `README.md` links to the license and to the Play Store listing; `SECURITY.md`, `CONTRIBUTING.md`, and `CODE_OF_CONDUCT.md` present at repo root; the privacy policy URL is linked from the Play Store listing
    - _Requirements: 29.3, 29.4, 29.5, 29.6, 29.11_
  - [ ] 97.3 Tag release `v1.0-public` in git
    - Marks the first fully-public production rollout
    - _Requirements: 29.9, 29.14_

---

## Notes

- Tasks marked with `*` are optional nice-to-haves and can be skipped without breaking the phase or the spec.
- Essential PBT tasks are never optional; every property from design §6 that is in scope for a phase has a required PBT sub-task in that phase.
- Every task references specific requirements for traceability, and every PBT task also carries its property number.
- Checkpoints: Phase 0 task 9, Phase 1 task 29, Phase 2 task 40, Phase 3 task 56, Phase 4 task 71, Phase 5 task 80, Phase 6 task 86, Phase 7 task 97. Each is the phase's exit validation and runs the full PBT suite up to that point (Phase 7's validation is procedural rather than PBT-driven — see Req 29). Ensure all tests pass; ask the user if questions arise.
- Distribution channel per phase follows the hybrid decision in design §12.1: Firebase App Distribution through Phase 2; Play Internal Testing (primary) + Firebase App Distribution (optional secondary) from Phase 3 onward.
- Branding: the product is "Aetheris Planner" (tagline: "Offline AI Planner for your Daily Life"). The Android `applicationId` is `dev.aetheris.planner`; the Kotlin base namespace is `dev.aetheris.planner.*`; the release / Play upload-key alias is `aetheris-release`. The source code is published in the public GitHub repository `aetheris` under the PolyForm Noncommercial 1.0.0 license.
- Phase 7 (Public Play Store Release) is conditional: it does not execute until the user has used `v1.0-ga` from Phase 6 on their own device for ≥ 2 weeks and has decided to proceed. If the user decides not to publish, the project remains fully available as source code on GitHub; no functionality depends on the Play listing (Req 29 AC 14).
