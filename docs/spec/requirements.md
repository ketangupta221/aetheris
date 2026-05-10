# Requirements Document

## Introduction

AI Daily Planner is an Android application for managing day-to-day personal and office work. Its center of gravity is a visual day timeline (comparable in spirit to the Structured app) augmented with richer productivity features including an on-device LLM chat assistant that accepts typed or spoken natural language, habit tracking, focus sessions, a daily planning and shutdown ritual, read-only calendar import, and widgets.

The application is **strictly offline-first**. No outbound network request is issued by any component at runtime by default. There is **no cloud LLM path** and **no cloud speech path**: LLM inference runs entirely on-device using MediaPipe LLM Inference (or llama.cpp Android bindings) against 1B–4B parameter open-weight models at 4-bit quantization that fit comfortably on a Samsung Galaxy S23 class device (Snapdragon 8 Gen 2, 8 GB RAM).

**Model strategy.** The application uses a two-model default layout for the LLM_Agent:

- **Always-bundled minimum: Gemma 3 1B Instruct Q4_K_M (~0.8 GB).** Ships inside the APK / AAB so the Chat_Interface works out of the box with zero network access. This minimum handles simple NL_Parser escalations (single-action, short inputs) and is always available as a safe fallback.
- **Recommended chat model: Gemma 2 2B Instruct Q4_K_M (~1.5 GB).** Prompted as a one-time consented download the first time the user opens the Chat_Interface, using the existing Network_Consent_Dialog. It is the preferred model for richer multi-step chat (bulk predicates, relative references, multi-action plans). If the user declines or defers, Chat continues to work on the bundled minimum.
- **Optional power-user models:** Phi-3.5 Mini 3.8B Instruct Q4_K_M (~2.3 GB), Gemma 3 4B Instruct Q4_K_M (~2.4 GB), Llama 3.2 3B Instruct Q4_K_M (~1.9 GB), Qwen 2.5 3B Instruct Q4_K_M (~1.9 GB). Each is a user-initiated download guarded by the same Network_Consent_Dialog; declining any download leaves the rest of the application functioning normally.

A small English speech model (whisper-tiny, ~75 MB) is also bundled inside the APK / AAB so voice capture works out of the box with zero network. Larger or multilingual speech models remain optional, user-consented downloads.

**Review-form UX.** The assistant never applies schedule changes autonomously. Every natural-language request that would mutate Tasks, Habits, or focus sessions is first turned into an **Action_Plan** (an ordered, validated list of actions referencing concrete Task / Event identifiers). The Action_Plan is always surfaced in an editable **Action_Plan_Review** form where the user can toggle individual actions on or off, edit any field in place, resolve ambiguous references (for example "the standup" when multiple candidates exist), add or remove actions, and reorder them. The user applies the plan with a single button (or an affirmative natural-language reply such as "go ahead" or "submit") or cancels to discard the plan. Read-only idempotent actions may skip the review.

To keep the device power-friendly and responsive, the application uses a **tiered natural-language routing design**. A single `Task_Router` component receives every natural-language input and first dispatches to the deterministic `NL_Parser`. The on-device `LLM_Agent` is only invoked when the `NL_Parser` is not confident enough to produce an action plan on its own. The `LLM_Agent` uses a **lazy lifecycle**: the model is not loaded at application cold start, is loaded on demand when the chat is opened or when the router escalates a request, and is unloaded after a configurable idle timeout. Inference is gated by an explicit power mode setting, runs on CPU by default, and never runs while the screen is off unless triggered by an explicit user notification action.

The `LLM_Agent`'s sole production responsibility for task capture is to emit a validated JSON action plan. The V1 approach to reliable structured output is **grammar-constrained decoding** (a GBNF grammar wired into the MediaPipe LLM Inference or llama.cpp runtime) combined with a bundled **Few_Shot_Examples** file of natural-language input and canonical action plan pairs, selected in-context by a deterministic on-device similarity score. An optional **LoRA_Adapter** may be bundled with the default base model to further bias it toward task-planning JSON. No training or fine-tuning is ever performed on the device.

All user data is stored locally on the device in an encrypted SQLite database. No analytics, telemetry, crash reporting, or advertising SDK that issues network calls is included.

This document specifies the feature set, behavior, and correctness properties derived from research on Structured, Todoist, TickTick, Sectograph, Motion, Notion Calendar, Sunsama, Google Tasks, Any.do, Fabric, and Reminders, and from Android platform guidance for on-device AI (MediaPipe LLM Inference API with GBNF grammar-constrained decoding, llama.cpp Android bindings, LiteRT / TensorFlow Lite, and — as an optional alternate backend only — Gemini Nano via ML Kit GenAI / AICore when the device exposes it on-device), on-device speech recognition (bundled whisper.cpp, Android SpeechRecognizer with EXTRA_PREFER_OFFLINE), local persistence (Room + SQLCipher), and scheduling (AlarmManager exact alarms, WorkManager, notification channels).

## Glossary

- **System**: The AI Daily Planner Android application as a whole.
- **Timeline_View**: The primary visual planner that renders tasks and events on a vertical day timeline.
- **Task_Store**: The local persistence layer responsible for creating, reading, updating, and deleting Task records.
- **Task**: A user-defined unit of work with a title, optional description, optional time range, optional due date, tags, priority, and completion state.
- **Event**: A time-bounded item imported from an external calendar file (read-only). Events are displayed alongside Tasks on the Timeline_View but cannot be edited by the System.
- **Recurrence_Engine**: The component that expands recurrence rules (based on RFC 5545 RRULE syntax) into concrete occurrences over a requested date range.
- **Reminder_Scheduler**: The component that schedules and cancels device alarms for Task reminders via Android AlarmManager (for time-sensitive reminders) and WorkManager (for non-time-critical background work).
- **Notification_Service**: The component that posts, updates, and cancels Android system notifications on the designated notification channels.
- **LLM_Agent**: The on-device large language model inference component that powers the chat interface and serves as the escalation path from the Task_Router. It runs entirely on-device with no cloud fallback. The primary runtime is the MediaPipe LLM Inference API loading a 4-bit quantized 1B–4B parameter open-weight model (default: **Gemma 3 1B Q4**, bundled inside the application package). llama.cpp Android bindings are an allowed alternate runtime for GGUF models. Gemini Nano via ML Kit GenAI / AICore MAY be used as an alternate on-device backend when available; it is optional and is not a cloud service. The LLM_Agent uses a **lazy lifecycle**: the model is not loaded at application cold start, is loaded on demand (chat open or router escalation), kept warm for a configurable idle timeout, and unloaded on idle timeout or Chat_Interface destruction. The LLM_Agent emits JSON action plans under **grammar-constrained decoding** (GBNF grammar) combined with few-shot examples selected from the Few_Shot_Examples file. The LLM_Agent issues no network calls at runtime.
- **Task_Router**: The component that routes natural-language input (typed or transcribed) first to the NL_Parser; if the NL_Parser fails or is not confident, the Task_Router invokes the LLM_Agent. The Task_Router also owns LLM lifecycle (load, warm, unload) and is the single code path permitted to invoke the LLM_Agent for task creation, update, and deletion from natural language.
- **NL_Parser**: The deterministic natural-language task parser that extracts structured Task fields (title, date, time, duration, recurrence, tags, priority) from free-text input. NL_Parser is used both as a fast path for direct input and as a validator for LLM_Agent output.
- **Speech_Recognizer**: The on-device speech-to-text component. The default engine is a bundled whisper-tiny (English, ~75 MB int8/Q5) model that ships inside the application package so that voice capture works out of the box with zero network access. The Android SpeechRecognizer API MAY be used as an alternate engine only when the system on-device speech service is installed, enabled, and verified offline-capable (EXTRA_PREFER_OFFLINE plus a service-availability check); otherwise the Speech_Recognizer falls back to the bundled whisper-tiny. Larger/multilingual Whisper models are optional user-initiated downloads. The Speech_Recognizer never uses any cloud speech service.
- **Chat_Interface**: The conversational UI surface through which the user interacts with the LLM_Agent via typed or spoken input.
- **Habit_Tracker**: The component that tracks recurring check-in behaviors and computes streak metrics.
- **Habit**: A user-defined recurring behavior with a schedule (daily, weekly, or custom days) and per-day completion records.
- **Focus_Session_Manager**: The component that runs Pomodoro-style focus sessions with configurable work and break durations.
- **Planning_Ritual**: A guided workflow that helps the user plan the upcoming day by selecting tasks, assigning time blocks, and setting intentions.
- **Shutdown_Ritual**: A guided workflow that helps the user review the day, reschedule incomplete tasks, and record reflections.
- **Quick_Capture**: A low-friction entry point (notification action, widget tap, or share intent target) for creating a Task in one step.
- **ICS_Importer**: The component that parses iCalendar (.ics, RFC 5545) files into Event records.
- **Search_Index**: The component that indexes Task and Habit text fields and returns matches for a query string.
- **Widget_Service**: The component that renders home-screen widgets showing the upcoming timeline and quick actions.
- **Backup_Manager**: The component that exports all user data to a single encrypted archive file and imports it back.
- **Local_Database**: The encrypted SQLite database (Room with SQLCipher) that holds all user data on the device.
- **Master_Key**: The encryption key used to encrypt the Local_Database, stored in the Android Keystore.
- **Model_Downloader**: An isolated, user-initiated module responsible for fetching optional model assets (larger LLM models, larger/multilingual speech models) from a documented source URL. It is the single code path in the application permitted to perform outbound network I/O, and runs only after explicit user consent via the Network_Consent_Dialog.
- **Few_Shot_Examples**: A bundled JSON file in the application package containing example pairs of (natural-language user input, canonical action plan JSON). Used as in-context examples by the LLM_Agent at inference time. No training is performed on the device.
- **LoRA_Adapter**: An optional Low-Rank Adaptation adapter file for a supported base model that biases the model toward producing task-planning JSON. Adapter size target under 30 MB. Bundled in the application package for supported base models when available; absence is acceptable and does not disable the LLM_Agent.
- **Network_Consent_Dialog**: The one-time in-app dialog that gates any outbound network call. It states the feature affected, the asset name, the asset size in megabytes, the source URL, that the network will be used once to fetch the asset, and that no user data will be transmitted. It offers Accept and Decline buttons.
- **RRULE**: A recurrence rule string that conforms to RFC 5545 §3.3.10.
- **Time_Block**: A Task occurrence placed on the Timeline_View with a specific start time and duration.
- **Entity_Resolver**: An on-device component that resolves natural-language references in user input (bulk predicates such as "all my meetings", relative task references such as "the standup", relative date references such as "tomorrow", and relative-to-task temporal references such as "before X") to concrete Task or Event identifiers in the Local_Database. It is invoked by the Task_Router and its output is consumed by the LLM_Agent as structured prompt context. All resolution rules are deterministic and on-device.
- **Action_Plan**: A validated, ordered list of actions (create_task, update_task, delete_task, create_habit, log_habit, start_focus_session) produced by the Task_Router pipeline (NL_Parser fast path, or LLM_Agent escalation path). Each action is self-contained and references concrete Task or Event identifiers by UUID, not by natural-language names.
- **Ambiguity_Marker**: A field on an Action_Plan action that flags a reference that could not be uniquely resolved (for example, "the standup" resolved to more than one candidate). The Action_Plan_Review must resolve every Ambiguity_Marker before the plan can be applied.
- **Action_Plan_Review**: An in-app surface that displays a proposed Action_Plan as an editable review form before application. It resolves any Ambiguity_Marker values via user picker inputs, allows per-field editing of every action, and supports adding, removing, toggling, and reordering actions. A single "Apply" button commits the finalized plan via the Task_Store; "Cancel" discards.

## Requirements

### Requirement 1: Visual Day Timeline

**User Story:** As a user, I want to see my day as a vertical visual timeline, so that I can understand at a glance what is scheduled, what is upcoming, and where I have free time.

#### Acceptance Criteria

1. THE Timeline_View SHALL render a 24-hour vertical timeline for a single calendar day, partitioned into hour rows.
2. WHEN the user opens the application, THE Timeline_View SHALL default to the current local calendar day.
3. THE Timeline_View SHALL render each Time_Block as a rectangle whose vertical position and height correspond to the Time_Block start time and duration at a scale of at least 48 density-independent pixels per hour.
4. WHEN two or more Time_Blocks overlap in time, THE Timeline_View SHALL render them side-by-side within the overlapping hour rows without occluding any Time_Block's title.
5. WHEN the user swipes horizontally on the Timeline_View, THE Timeline_View SHALL navigate to the previous or next calendar day.
6. THE Timeline_View SHALL render a visible "now" indicator on the row corresponding to the current local time, refreshed at least once per minute while the Timeline_View is visible.
7. WHERE the user has enabled the "week view" option, THE Timeline_View SHALL render seven adjacent day columns for the week containing the selected day.

### Requirement 2: Task Management

**User Story:** As a user, I want to create, edit, reorder, complete, and delete tasks, so that I can keep my plan current.

#### Acceptance Criteria

1. THE Task_Store SHALL persist each Task with the following fields: identifier, title, description, start time, end time, due date, priority (one of low, medium, high), tag set, recurrence rule, reminder offsets, completion state, and creation timestamp.
2. WHEN the user submits a new Task through any input surface, THE Task_Store SHALL assign a unique identifier and persist the Task within 200 milliseconds on a mid-range device (Snapdragon 7-series or equivalent, 6 GB RAM).
3. THE Task_Store SHALL enforce the invariant that a Task's start time is less than or equal to its end time whenever both are set.
4. WHEN the user edits an existing Task, THE Task_Store SHALL persist the updated Task and preserve its identifier and creation timestamp.
5. WHEN the user marks a Task as complete, THE Task_Store SHALL record the completion timestamp and THE Timeline_View SHALL visually distinguish the completed Task within one render frame.
6. WHEN the user deletes a Task, THE Task_Store SHALL remove the Task and THE Reminder_Scheduler SHALL cancel all pending reminders associated with the Task.
7. WHERE a Task has subtasks, THE Task_Store SHALL persist subtasks as Task records that reference the parent Task identifier.

### Requirement 3: Recurring Tasks

**User Story:** As a user, I want recurring tasks that repeat on a schedule, so that I do not have to recreate them each time.

#### Acceptance Criteria

1. THE Recurrence_Engine SHALL accept RRULE strings conforming to RFC 5545 §3.3.10 with support for FREQ values DAILY, WEEKLY, MONTHLY, and YEARLY, and for the INTERVAL, BYDAY, BYMONTHDAY, COUNT, and UNTIL parameters.
2. WHEN the Timeline_View requests occurrences for a date range, THE Recurrence_Engine SHALL return all occurrences whose start times fall within the range, in ascending chronological order.
3. IF an RRULE string is malformed, THEN THE Recurrence_Engine SHALL reject the input and return a descriptive error identifying the first invalid token and its position.
4. THE Recurrence_Engine SHALL expose a pretty printer that formats an RRULE value back into its canonical RFC 5545 string form.
5. FOR ALL valid RRULE values, parsing the canonical string then printing then parsing SHALL produce an equivalent RRULE value (round-trip property).
6. WHEN the user completes a single occurrence of a recurring Task, THE Task_Store SHALL record completion for that occurrence only and SHALL leave other occurrences unchanged.
7. WHEN the user edits a single occurrence of a recurring Task with the "only this occurrence" option, THE Task_Store SHALL persist an exception for that occurrence and SHALL leave the base recurrence unchanged.

### Requirement 4: Local Reminders and Notifications

**User Story:** As a user, I want reliable notifications for my tasks, so that I do not miss them.

#### Acceptance Criteria

1. THE Reminder_Scheduler SHALL support per-Task reminder offsets expressed as a set of non-negative minutes before the Task start time.
2. WHEN a Task with at least one reminder offset is created or updated, THE Reminder_Scheduler SHALL register an exact alarm via AlarmManager.setAlarmClock (or equivalent exact API) for each offset.
3. WHEN a scheduled reminder fires, THE Notification_Service SHALL post a notification on the "Task Reminders" notification channel with the Task title, start time, and a "Mark complete" action.
4. WHEN the user taps the "Mark complete" notification action, THE Task_Store SHALL mark the Task as complete and THE Notification_Service SHALL dismiss the notification.
5. WHEN a Task's scheduled start time passes without the Task being marked complete, THE Notification_Service SHALL post a "missed task" notification on the "Missed Tasks" notification channel no later than 60 seconds after the start time elapses, with "Reschedule" and "Mark complete" actions.
6. IF the SCHEDULE_EXACT_ALARM permission is not granted, THEN THE System SHALL present a one-time in-app dialog explaining the need for exact alarms, offer a deep link to the system setting, and THE Reminder_Scheduler SHALL fall back to inexact alarms via AlarmManager.setWindow until the permission is granted.
7. WHEN the device reboots, THE Reminder_Scheduler SHALL re-register all pending reminders on receipt of the BOOT_COMPLETED broadcast.
8. THE Notification_Service SHALL define the notification channels "Task Reminders", "Missed Tasks", "Habit Check-ins", and "Focus Sessions" with user-configurable importance levels.
9. FOR ALL Task identifiers, scheduling a reminder and then canceling it SHALL leave the set of pending alarms unchanged relative to its state before scheduling (cancel-after-schedule idempotence).

### Requirement 5: Offline-First Operation and Fully Local Data Storage

**User Story:** As a user, I want the application to work fully offline with my data stored only on my device, so that I retain privacy, cannot leak data to the network, and can use the app anywhere without an internet connection.

This requirement is the single source of truth for every network-consent interaction in the application. All other requirements defer to it.

#### Acceptance Criteria

1. THE System SHALL default to a strict offline mode in which no outbound network request is issued by any component at runtime.
2. THE System SHALL NOT perform LLM inference, speech recognition, search, analytics, telemetry, crash reporting, advertising, or any other core operation via a network call under any circumstances unless the user has explicitly consented in advance through the Network_Consent_Dialog.
3. THE System SHALL store all user-generated data (Tasks, Habits, chat history, focus session history, settings, ritual entries, imported Events, consent decisions) in the Local_Database on the device.
4. WHEN the user performs any core operation (create, read, update, delete a Task; chat with LLM_Agent; transcribe speech via Speech_Recognizer; run a focus session; log a Habit; import an .ics file from local storage; export or import a backup from local storage), THE System SHALL complete the operation without issuing any network request.
5. WHEN a feature requires an asset that is not available locally (for example an LLM model weight file, a larger speech recognition model, or a language pack), THE System SHALL present the Network_Consent_Dialog that states the feature affected, the asset name, the asset size in megabytes, the source URL, the fact that the network will be used once to fetch the asset, and the fact that no user data will be transmitted as part of the download, and SHALL proceed only after explicit user confirmation.
6. IF the user declines the Network_Consent_Dialog, THEN the dependent feature SHALL remain unavailable, the System SHALL record the decision in the Local_Database, and the rest of the application SHALL continue to function normally.
7. THE System SHALL NOT include any analytics, telemetry, crash reporting, or advertising SDK that issues network calls in its production build.
8. THE System SHALL NOT declare the android.permission.INTERNET permission in the production manifest of any module other than the isolated Model_Downloader module; the Model_Downloader module SHALL run only after explicit user consent recorded via the Network_Consent_Dialog. Implementation approach: the Model_Downloader SHALL be packaged as a dedicated Android module (dynamic feature module or equivalent) with its own manifest declaring android.permission.INTERNET, and the main application module's manifest SHALL NOT declare android.permission.INTERNET. No runtime code path outside the Model_Downloader module SHALL open a network socket.
9. THE Master_Key used to encrypt the Local_Database (see Requirement 6) SHALL be generated and used entirely on-device and SHALL never leave the device.

### Requirement 6: Local Database Encryption

**User Story:** As a user, I want my local data encrypted at rest, so that my schedule and notes are protected if my device is lost.

#### Acceptance Criteria

1. THE Local_Database SHALL be encrypted at rest using SQLCipher with AES-256 (or an equivalent authenticated encryption scheme for SQLite) integrated with Room.
2. THE System SHALL generate the Master_Key on first launch using a cryptographically secure random source and SHALL store the Master_Key in the Android Keystore with user-authentication-bound key protection where available.
3. WHEN the System opens the Local_Database, THE System SHALL retrieve the Master_Key from the Android Keystore and SHALL NOT write the Master_Key to disk in plaintext.
4. IF Master_Key retrieval fails after three consecutive attempts, THEN THE System SHALL surface a recovery flow that requires the user to re-authenticate with device credentials before retrying.

### Requirement 7: On-Device LLM Chat Assistant

**User Story:** As a user, I want a chat assistant that runs entirely on my device with no cloud path at all, so that I can manage my schedule with natural language with guaranteed privacy and offline availability.

**Reference device for performance budgets:** Samsung Galaxy S23 (Snapdragon 8 Gen 2, 8 GB RAM) running Android 13 or later. All quantitative targets in this requirement are measured on this device class.

#### Acceptance Criteria

1. THE LLM_Agent SHALL perform all inference on-device. THE LLM_Agent SHALL NOT provide, expose, or fall back to any cloud inference path under any circumstances.
2. THE LLM_Agent SHALL NOT issue any network call at runtime. Model weight files SHALL be read from local storage only.
3. THE LLM_Agent SHALL use the MediaPipe LLM Inference API as the primary runtime, loading models in the MediaPipe .task bundle format (which may wrap GGUF weights) and executing inference on the CPU or on the GPU / NNAPI delegate when available on the device.
4. THE LLM_Agent MAY use llama.cpp Android bindings as an allowed alternate runtime for GGUF models where the MediaPipe runtime is unsuitable for a chosen model.
5. WHERE Gemini Nano via ML Kit GenAI / AICore is installed and exposed as an on-device-only backend on the device, THE LLM_Agent MAY use it as an alternate on-device backend. Gemini Nano SHALL NOT be treated as a primary backend and SHALL NOT be treated as a cloud service; if its on-device availability cannot be verified, THE LLM_Agent SHALL NOT invoke it.
6. THE LLM_Agent SHALL support the following open-weight model tier on Samsung Galaxy S23 class devices, all at 4-bit quantization (Q4_K_M or equivalent). Each entry is labeled with its role: "bundled minimum" (always shipped inside the APK / AAB, zero-network out of the box), "recommended" (the preferred model for the Chat_Interface, acquired via a one-time consented download), or "optional" (power-user downloads):
   - Gemma 3 1B Instruct Q4_K_M — **bundled minimum**, ~0.8 GB on disk. Used for simple NL_Parser escalations and as the always-available fallback when the recommended model is not present.
   - Gemma 2 2B Instruct Q4_K_M — **recommended** for the Chat_Interface, ~1.5 GB on disk. Default target for multi-action chat when available locally.
   - Phi-3.5 Mini 3.8B Instruct Q4_K_M — optional, ~2.3 GB on disk.
   - Gemma 3 4B Instruct Q4_K_M — optional, ~2.4 GB on disk.
   - Llama 3.2 3B Instruct Q4_K_M — optional, ~1.9 GB on disk.
   - Qwen 2.5 3B Instruct Q4_K_M — optional, ~1.9 GB on disk.
7. THE application SHALL bundle Gemma 3 1B Instruct Q4_K_M inside the APK / AAB as the always-available minimum model, so that the Chat_Interface can function with zero network access at first launch. WHEN the user first opens the Chat_Interface AND the recommended model (Gemma 2 2B Instruct Q4_K_M) is not yet present locally, THE System SHALL present the Network_Consent_Dialog (as defined in Requirements 5 and 24) offering the recommended-model download with the label "Recommended for accurate multi-step chat" and SHALL allow the user to choose one of three actions: "Accept" (download and use the recommended model), "Decline" (do not download; keep using the bundled minimum), or "Use minimum model for now" (do not download, suppress re-prompting until the user opens Settings → Models). The bundled minimum SHALL be shipped either as an install-time asset in the APK / AAB or via Play Asset Delivery install-time assets if size constraints require; no runtime download SHALL be necessary to use the Chat_Interface with the bundled minimum.
8. WHERE the user chooses any non-bundled model from the supported tier listed in acceptance criterion 6 (recommended or optional), THE LLM_Agent SHALL acquire that model only through a user-initiated, user-consented one-time download via the Model_Downloader module, gated by the Network_Consent_Dialog defined in Requirement 5. Subsequent use of the downloaded model SHALL be fully offline.
9. THE LLM_Agent SHALL meet the following per-model performance budgets on a Samsung Galaxy S23 class device in balanced power mode when the model is already loaded (warm) or when the model is being loaded from local storage (cold). All budgets are upper bounds; the values are minimum requirements that the chosen runtime must satisfy.

| Model | Warm first-token | Cold first-token (incl. load) | Sustained generation | Cold-load from disk | Peak process RSS |
| --- | --- | --- | --- | --- | --- |
| Gemma 3 1B Q4_K_M (bundled minimum) | ≤ 1.5 s | ≤ 2.5 s | ≥ 15 tok/s | ≤ 1500 ms | ≤ 1.2 GB |
| Gemma 2 2B Q4_K_M (recommended) | ≤ 2.0 s | ≤ 4.0 s | ≥ 10 tok/s | ≤ 3000 ms | ≤ 2.2 GB |
| Phi-3.5 Mini 3.8B Q4_K_M (optional) | ≤ 3.0 s | ≤ 6.0 s | ≥ 6 tok/s | ≤ 4500 ms | ≤ 3.2 GB |
| Gemma 3 4B Q4_K_M (optional) | ≤ 3.0 s | ≤ 6.0 s | ≥ 6 tok/s | ≤ 4500 ms | ≤ 3.2 GB |
| Llama 3.2 3B Q4_K_M (optional) | ≤ 3.0 s | ≤ 6.0 s | ≥ 6 tok/s | ≤ 4500 ms | ≤ 3.2 GB |
| Qwen 2.5 3B Q4_K_M (optional) | ≤ 3.0 s | ≤ 6.0 s | ≥ 6 tok/s | ≤ 4500 ms | ≤ 3.2 GB |

10. WHEN the user submits the first chat message after a cold start (model not yet loaded), THE LLM_Agent SHALL return the first token within the cold first-token budget listed in acceptance criterion 9 for the active model. Subsequent prompts within the idle window SHALL meet the warm first-token budget in acceptance criterion 9.
11. THE LLM_Agent SHALL receive, as structured context for every turn, the user's current local time, time zone, and a compact representation of Tasks scheduled within the surrounding 7-day window.
12. IF the LLM_Agent produces output that does not parse as a valid action plan (see Requirement 9), THEN THE Chat_Interface SHALL present the response as plain conversational text and SHALL NOT modify any Task or Habit.
13. THE Chat_Interface SHALL persist chat history in the Local_Database with per-conversation pagination and SHALL allow the user to delete individual messages or an entire conversation.
14. WHEN the user requests deletion of a conversation, THE Chat_Interface SHALL remove all associated messages from the Local_Database within one second.
15. THE LLM_Agent SHALL load the model into memory only when one of the following triggers occurs: (a) the user opens the Chat_Interface, or (b) the Task_Router determines that the NL_Parser's confidence is below threshold for a user request in progress. THE LLM_Agent SHALL NOT load the model at application cold start.
16. WHEN the model is loaded, THE LLM_Agent SHALL keep the model resident for a configurable idle timeout (default 60 seconds) after the last successful inference completes, and SHALL unload the model from memory when the timeout elapses or when the Chat_Interface is destroyed, whichever occurs first.
17. WHILE the model is unloaded, THE LLM_Agent SHALL hold no native memory, no GPU context, and no wake locks attributable to inference.
18. THE LLM_Agent SHALL NOT run inference while the device screen is off (including when the device is in Doze), except when triggered by an explicit user-initiated notification action that requires inference.
19. THE LLM_Agent SHALL run inference on at most one request at a time and SHALL reject concurrent requests with a "busy" error returned to the Task_Router. The Chat_Interface SHALL queue additional user requests and dispatch them serially.
20. THE LLM_Agent SHALL run on CPU by default and SHALL use the GPU or NNAPI delegate only when a Settings toggle "Allow GPU acceleration" is enabled. The default value of this toggle SHALL be OFF to preserve battery.
21. THE LLM_Agent SHALL expose a "Power mode" Settings option with values {battery-first, balanced, performance}. In battery-first mode, THE LLM_Agent SHALL limit generation to 256 output tokens and SHALL use single-thread CPU inference. In balanced mode (default), THE LLM_Agent SHALL permit up to 512 output tokens when the active model is Gemma 3 1B Q4_K_M, and up to 1024 output tokens when the active model is Gemma 2 2B Q4_K_M or larger (because these models need more headroom to emit multi-action plans), and up to 4 threads. In performance mode, THE LLM_Agent SHALL permit up to 1024 output tokens and all available big-core threads. THE Task_Router SHALL respect the selected mode.
22. THE LLM_Agent SHALL expose an opt-in on-device "energy used by inference" counter (milliseconds of CPU time accumulated per day) that the user can view in Settings → Diagnostics. No telemetry: this counter SHALL stay on-device.
23. THE cold model load times from local storage on a Samsung Galaxy S23 class device SHALL meet the per-model cold-load budgets listed in acceptance criterion 9 (Gemma 3 1B Q4_K_M ≤ 1500 ms, Gemma 2 2B Q4_K_M ≤ 3000 ms, and ≤ 4500 ms for the optional 3B–4B models).
24. THE peak process resident set size during inference on a Samsung Galaxy S23 class device SHALL meet the per-model peak RSS budgets listed in acceptance criterion 9 (Gemma 3 1B Q4_K_M ≤ 1.2 GB, Gemma 2 2B Q4_K_M ≤ 2.2 GB, and ≤ 3.2 GB for the optional 3B–4B models).

### Requirement 8: Natural-Language Task Parsing

**User Story:** As a user, I want to type or speak a task in plain English and have the app extract the date, time, duration, and tags, so that I can capture items in one line.

#### Acceptance Criteria

1. THE NL_Parser SHALL accept a free-text input string and SHALL produce a structured Task candidate containing, at minimum, the title, start time (if present), end time (if present), due date (if present), recurrence rule (if present), tag set, and priority (if present).
2. THE NL_Parser SHALL support the date expressions "today", "tomorrow", weekday names, dates in ISO-8601 form (YYYY-MM-DD), and relative offsets of the form "in N minutes", "in N hours", "in N days", and "next week".
3. THE NL_Parser SHALL support time expressions in 12-hour form ("3pm", "3:30pm") and 24-hour form ("15:00", "15:30").
4. THE NL_Parser SHALL extract tags from tokens prefixed with "#" and SHALL extract priority from the tokens "p1", "p2", "p3" (mapped to high, medium, low respectively).
5. THE NL_Parser SHALL expose a pretty printer that renders a Task candidate back into a canonical natural-language string.
6. FOR ALL Task candidates produced by the NL_Parser, applying the pretty printer and then re-parsing the output SHALL produce a Task candidate equivalent to the original (round-trip property).
7. FOR ALL input strings, invoking the NL_Parser twice on the same input SHALL produce identical Task candidates (determinism).
8. IF the NL_Parser cannot identify a non-empty title, THEN THE NL_Parser SHALL return a parse error identifying the input as "untitled".
9. IF the NL_Parser detects any of the patterns listed in Requirement 25 as "always escalate" (multi-clause inputs joined by "and" or "then" producing more than one action, bulk predicates such as "all" / "every" / "my meetings", relative task references such as "the X" / "my X", or relative-to-task temporal references such as "before X" / "after X" / "at the same time as X"), THEN THE NL_Parser SHALL immediately return with confidence 0.0 so that the Task_Router escalates to the LLM_Agent, rather than producing a partial parse.

### Requirement 9: LLM Action Plans and Validation

**User Story:** As a user, I want the chat assistant to reliably create, update, and delete my tasks when I ask it to, so that voice and chat become a first-class input method.

#### Acceptance Criteria

1. THE LLM_Agent SHALL emit, when asked to modify the schedule, a JSON action plan containing an ordered list of actions drawn from the set {create_task, update_task, delete_task, create_habit, log_habit, start_focus_session}.
2. THE System SHALL validate each action plan against a JSON schema and SHALL reject the plan if any action fails schema validation or references an identifier that does not exist in the Task_Store.
3. WHEN a valid Action_Plan is produced, THE Task_Router SHALL route the plan to the Action_Plan_Review surface (defined in Requirement 28) for review and confirmation before THE Task_Store applies any action, except for read-only idempotent actions which MAY be applied without review.
4. WHEN the user confirms a valid action plan, THE Task_Store SHALL apply all actions in order within a single transaction and SHALL roll back all actions if any action fails.
5. FOR ALL valid action plans, applying the plan to a Task_Store state and then applying the plan again SHALL produce the same final state as applying the plan once (idempotence under confirmation replay).
6. IF validation fails, THEN THE Chat_Interface SHALL present a human-readable explanation of the first failing action and SHALL offer the user a "retry" affordance that re-prompts the LLM_Agent with the validation error appended to the context.
7. THE LLM_Agent SHALL perform grammar-constrained decoding such that the emitted token stream is restricted to sequences that match the JSON schema for the action plan. Implementation note: a GBNF grammar passed to the MediaPipe LLM Inference or llama.cpp runtime, or an equivalent structured-decoding mechanism.
8. THE LLM_Agent's system prompt SHALL include a user-selectable set of few-shot examples drawn from the Few_Shot_Examples file defined in Requirement 26. The default selection SHALL be the top-K most-similar examples to the current input, chosen by a lightweight on-device similarity score (token overlap, or MiniLM if present). K SHALL default to 6 and SHALL be configurable between 0 and 12.
9. IF grammar-constrained decoding detects that the candidate next-token distribution contains no tokens permitted by the grammar, THEN THE LLM_Agent SHALL fall back to emitting a conversational response marked "unable to construct a valid action plan" and SHALL NOT modify any Task or Habit.
10. THE Action_Plan JSON schema SHALL include the following fields per action: `action_type` (one of create_task, update_task, delete_task, create_habit, log_habit, start_focus_session), `target_id` (for update and delete actions, a Task or Event UUID referencing an existing record in the Local_Database), `payload` (the fields to set for create or update actions), `display_label` (a natural-language summary of the action shown in the Action_Plan_Review), `ambiguity` (an optional Ambiguity_Marker as defined in Requirement 27), and `enabled` (boolean, default true; when false the action is skipped by apply).

### Requirement 10: On-Device Speech Input

**User Story:** As a user, I want to add and manage tasks by speaking, so that I can capture items hands-free, with guaranteed on-device processing and no cloud speech path.

#### Acceptance Criteria

1. WHEN the user taps the microphone affordance in the Chat_Interface or Quick_Capture, THE Speech_Recognizer SHALL begin capturing audio and SHALL transcribe speech to text.
2. THE Speech_Recognizer SHALL bundle a whisper-tiny English model (approximately 75 MB at int8 or Q5 quantization) inside the application package as the default offline speech engine, so that voice capture works out of the box with zero network access and zero asset download.
3. THE Speech_Recognizer MAY use the Android SpeechRecognizer API as an alternate engine only when the following are all true: the system on-device speech service is installed and enabled for the active locale, the intent EXTRA_PREFER_OFFLINE flag is set to true, and the System has verified on-device availability via a service availability check (for example SpeechRecognizer.checkRecognitionSupport, or the equivalent onSupportResult / onLanguageDetection callbacks where available).
4. IF on-device availability of the Android SpeechRecognizer cannot be confirmed for the active locale, THEN THE Speech_Recognizer SHALL fall back to the bundled whisper-tiny model.
5. THE Speech_Recognizer SHALL NOT use any cloud speech service under any circumstances and SHALL NOT transmit audio or transcriptions over the network.
6. WHERE the user requests a larger or multilingual speech model (for example whisper-base or whisper-small), THE Speech_Recognizer SHALL acquire the model only through a user-initiated, user-consented one-time download via the Model_Downloader module, gated by the Network_Consent_Dialog defined in Requirement 5. Subsequent use of the downloaded model SHALL be fully offline.
7. WHEN the Speech_Recognizer produces a final transcription, THE Chat_Interface or Quick_Capture SHALL insert the transcription as the input text and SHALL allow the user to review and edit before submission.
8. IF microphone permission is not granted, THEN THE System SHALL request the RECORD_AUDIO permission and SHALL disable the microphone affordance until the permission is granted.

### Requirement 11: Habit Tracking

**User Story:** As a user, I want to track daily and weekly habits with visible streaks, so that I can build consistency.

#### Acceptance Criteria

1. THE Habit_Tracker SHALL persist each Habit with a title, schedule (one of daily, specific weekdays, or every N days), reminder time, and per-day completion records.
2. WHEN the user checks a Habit as complete for a given day, THE Habit_Tracker SHALL persist the completion record keyed by the habit identifier and the local calendar date.
3. THE Habit_Tracker SHALL compute the current streak for a Habit as the count of consecutive scheduled days, ending at today, for which a completion record exists.
4. THE Habit_Tracker SHALL compute the longest streak for a Habit as the maximum count of consecutive scheduled days for which completion records exist over the Habit's lifetime.
5. FOR ALL Habits and all dates d, the current-streak value SHALL be greater than or equal to zero and SHALL be less than or equal to the longest-streak value (invariant).
6. WHEN the user unchecks a Habit for a day, THE Habit_Tracker SHALL remove that completion record and SHALL recompute the current and longest streaks.
7. WHEN the reminder time for a scheduled Habit is reached, THE Notification_Service SHALL post a check-in notification on the "Habit Check-ins" channel with "Done" and "Skip" actions.

### Requirement 12: Focus Sessions

**User Story:** As a user, I want Pomodoro-style focus sessions linked to a task, so that I can work in protected intervals.

#### Acceptance Criteria

1. THE Focus_Session_Manager SHALL allow the user to start a focus session with configurable work duration (default 25 minutes) and break duration (default 5 minutes) and an optional linked Task.
2. WHILE a focus session is active, THE Focus_Session_Manager SHALL display a persistent foreground notification on the "Focus Sessions" channel showing elapsed time and remaining time, updated at least once per second.
3. WHEN the configured work duration elapses, THE Focus_Session_Manager SHALL play an audible cue respecting the device's ringer and do-not-disturb settings, and THE Notification_Service SHALL post a "time for a break" notification.
4. THE Focus_Session_Manager SHALL persist each completed session with start time, end time, paused duration, and linked Task identifier.
5. THE Focus_Session_Manager SHALL enforce the invariant that elapsed active time plus paused time equals total session time for every persisted session.
6. WHEN the user pauses an active session, THE Focus_Session_Manager SHALL suspend the countdown and SHALL resume it from the paused point when the user resumes.

### Requirement 13: Daily Planning Ritual

**User Story:** As a user, I want a guided morning routine that helps me select and time-block tasks, so that I start my day with a clear plan.

#### Acceptance Criteria

1. THE Planning_Ritual SHALL present a multi-step workflow that, at minimum, includes: review unfinished tasks from prior days, select tasks for today, assign time blocks on today's timeline, and set an optional "intention of the day" text note.
2. WHEN the user schedules a Planning_Ritual reminder, THE Reminder_Scheduler SHALL post a notification at the configured local time on each configured weekday.
3. WHEN the user completes a Planning_Ritual step, THE Planning_Ritual SHALL persist the user's selections and SHALL allow navigation to the next step.
4. WHEN the user exits the Planning_Ritual before completing it, THE Planning_Ritual SHALL persist partial state and SHALL resume at the same step when re-entered on the same local calendar day.

### Requirement 14: Daily Shutdown Ritual

**User Story:** As a user, I want a guided end-of-day routine that lets me review the day and reschedule open items, so that nothing is lost overnight.

#### Acceptance Criteria

1. THE Shutdown_Ritual SHALL present a multi-step workflow that, at minimum, includes: review today's completed and incomplete tasks, reschedule or mark incomplete tasks as abandoned, record an optional reflection note, and confirm shutdown.
2. WHEN the user selects "reschedule" for an incomplete task, THE Shutdown_Ritual SHALL allow the user to pick a new date and SHALL update the Task via the Task_Store.
3. WHEN the user confirms shutdown, THE Shutdown_Ritual SHALL persist the reflection note keyed by the local calendar date.

### Requirement 15: Quick Capture

**User Story:** As a user, I want to capture a task in one action from anywhere, so that low-friction capture keeps me using the app.

#### Acceptance Criteria

1. THE Quick_Capture SHALL be accessible from (a) a persistent notification on the "Task Reminders" channel, (b) a home-screen widget affordance, and (c) an Android Sharesheet target that accepts text/plain.
2. WHEN the user submits text through Quick_Capture, THE NL_Parser SHALL parse the text and THE Task_Store SHALL persist the resulting Task.
3. WHEN Quick_Capture receives text via the Sharesheet, THE Quick_Capture SHALL pre-fill the input with the shared text and SHALL require explicit user confirmation before persisting.

### Requirement 16: iCalendar Import

**User Story:** As a user, I want to import an .ics calendar file so that my existing events appear on my timeline without any cloud sync.

#### Acceptance Criteria

1. THE ICS_Importer SHALL accept iCalendar files conforming to RFC 5545 and SHALL parse VEVENT components including SUMMARY, DTSTART, DTEND, DURATION, RRULE, EXDATE, and TZID properties.
2. WHEN the user selects an .ics file via the Android document picker, THE ICS_Importer SHALL parse the file and SHALL persist each VEVENT as a read-only Event in the Local_Database.
3. IF the file is not a valid iCalendar file, THEN THE ICS_Importer SHALL reject the import and SHALL display an error message identifying the first parse failure with line and column.
4. THE ICS_Importer SHALL expose a pretty printer that serializes an Event back into an RFC 5545 VEVENT block.
5. FOR ALL Events produced by the ICS_Importer, serializing and then re-parsing SHALL produce an equivalent Event (round-trip property).
6. THE System SHALL render imported Events on the Timeline_View with a visual treatment that distinguishes them from Tasks and SHALL NOT allow editing of Event fields.

### Requirement 17: Search

**User Story:** As a user, I want to search across my tasks, habits, and notes, so that I can find items quickly.

#### Acceptance Criteria

1. THE Search_Index SHALL index the title, description, tags, and reflection notes of all Tasks, Habits, and ritual entries.
2. WHEN the user submits a non-empty query string, THE Search_Index SHALL return matching items ranked by relevance within 150 milliseconds on a Local_Database containing up to 10,000 items on a mid-range device.
3. FOR ALL query strings and all item sets, the result set returned by the Search_Index SHALL be a subset of the indexed items.
4. WHEN the query string is empty, THE Search_Index SHALL return no results.
5. WHEN a Task or Habit is created, updated, or deleted, THE Search_Index SHALL reflect the change in subsequent queries.

### Requirement 18: Home-Screen Widgets

**User Story:** As a user, I want to see today's timeline and capture tasks without opening the app, so that I can stay on top of my day from the home screen.

#### Acceptance Criteria

1. THE Widget_Service SHALL provide an "Agenda" app widget that displays the next N Time_Blocks starting from the current local time, where N is the maximum that fits the widget's assigned size.
2. THE Widget_Service SHALL provide a "Quick Capture" app widget that opens Quick_Capture in one tap.
3. WHEN the underlying Tasks change, THE Widget_Service SHALL refresh the Agenda widget within 60 seconds.
4. WHEN the user taps a Time_Block in the Agenda widget, THE System SHALL open the application to the Timeline_View focused on that Time_Block.

### Requirement 19: Backup, Export, and Import

**User Story:** As a user, I want to back up and restore my data to a local file, so that I can move between devices without using any cloud.

#### Acceptance Criteria

1. THE Backup_Manager SHALL export all user data (Tasks, Habits, chat history, focus session history, ritual entries, settings, imported Events) to a single file in a documented, versioned format.
2. THE Backup_Manager SHALL encrypt the export file using a user-supplied passphrase with a key-derivation function that uses at least 600,000 PBKDF2 iterations or an equivalent-strength KDF such as Argon2id.
3. THE Backup_Manager SHALL expose a pretty printer (serializer) and a parser for the export format.
4. FOR ALL user data states, exporting to a backup file and then importing the backup into an empty Local_Database SHALL produce a Local_Database state equivalent to the original (round-trip property).
5. IF the user-supplied passphrase is incorrect during import, THEN THE Backup_Manager SHALL reject the import with a descriptive error and SHALL NOT modify the Local_Database.
6. THE Backup_Manager SHALL allow the user to schedule automatic backups to a user-selected local directory on a configurable cadence (daily, weekly, or off).

### Requirement 20: Accessibility and Internationalization

**User Story:** As a user with accessibility needs, I want the app to meet common accessibility standards, so that I can use it effectively.

#### Acceptance Criteria

1. THE System SHALL expose content descriptions for all non-text interactive elements such that TalkBack announces a meaningful label for each element.
2. THE System SHALL honor the system font scale up to 200 percent without clipping text or losing functionality.
3. THE Timeline_View SHALL maintain a contrast ratio of at least 4.5:1 between foreground text and background colors in both light and dark themes.
4. THE System SHALL localize all user-facing strings through Android resource files and SHALL ship with at least English (en) as a baseline.
5. WHEN the device locale changes, THE System SHALL render dates, times, and weekday names according to the new locale on the next screen render.

### Requirement 21: Permissions and Onboarding

**User Story:** As a user, I want a clear first-run experience that explains each permission, so that I understand why the app needs access.

#### Acceptance Criteria

1. WHEN the user launches the application for the first time, THE System SHALL present an onboarding flow that describes the local-only data model and the purpose of each runtime permission the app may request.
2. THE System SHALL request runtime permissions only at the point of use (just-in-time) and SHALL explain the purpose of each permission in the request dialog rationale.
3. IF the user denies a permission, THEN THE System SHALL continue to function with the dependent feature disabled and SHALL clearly indicate which features are unavailable.

### Requirement 22: Theming and Appearance

**User Story:** As a user, I want light, dark, and system-matched themes, so that the app fits my device and environment.

#### Acceptance Criteria

1. THE System SHALL provide three theme modes: light, dark, and follow system.
2. WHEN the device is running Android 12 or later and the user enables Material You, THE System SHALL derive its color palette from the system dynamic color palette.
3. WHEN the theme changes, THE System SHALL apply the new theme to all visible surfaces within one render frame.

### Requirement 23: Performance and Resource Budgets

**User Story:** As a user, I want the app to be responsive and battery-friendly, so that it is usable throughout the day.

#### Acceptance Criteria

1. THE Timeline_View SHALL render the current day with 50 Time_Blocks in 300 milliseconds or less, measured from navigation start to first frame, on a mid-range device.
2. THE System SHALL keep cold-start time to the Timeline_View under 1,500 milliseconds on a mid-range device.
3. WHILE no focus session is active, THE System SHALL hold no wake lock and SHALL keep background CPU usage under 1 percent averaged over any 10-minute window.
4. THE LLM_Agent SHALL not run inference while the screen is off unless triggered by an explicit notification action.
5. WHILE the LLM_Agent model is not loaded, the application process SHALL hold no LLM-attributable native memory.
6. WHEN the user cold-launches the Chat_Interface (including the on-demand model load from local storage), THE System SHALL present a ready prompt within 2.5 seconds on a Samsung Galaxy S23 class device using the bundled default model (Gemma 3 1B Q4).
7. WHILE the Chat_Interface is not in the foreground, background battery draw attributable to the LLM_Agent SHALL be zero.

### Requirement 24: Network Consent and Reduced Functionality

**User Story:** As a user, I want to be asked before the application ever uses the network, so that I remain in full control of whether and when outbound traffic happens.

This requirement concretizes the Network_Consent_Dialog referenced by Requirement 5.

#### Acceptance Criteria

1. THE Network_Consent_Dialog SHALL display, for each optional asset, all of the following fields: the name of the feature affected, the asset name, the asset size in megabytes, the source URL from which the asset will be fetched, a plain-language statement that the network will be used one time to fetch the asset, and a plain-language statement that no user data will be transmitted as part of the download.
2. THE Network_Consent_Dialog SHALL present exactly two primary actions labeled "Accept" and "Decline".
3. WHEN the user selects "Accept" in the Network_Consent_Dialog, THE Model_Downloader SHALL fetch the asset over the network, THE System SHALL record the consent decision keyed by the asset identifier in the Local_Database, and THE System SHALL NOT re-show the Network_Consent_Dialog for the same asset on subsequent launches.
4. WHEN the user selects "Decline" in the Network_Consent_Dialog, THE System SHALL record the decision keyed by the asset identifier in the Local_Database, THE dependent feature SHALL remain unavailable, and the rest of the application SHALL continue to function normally with no degradation of unrelated features.
5. THE System SHALL provide a Settings surface that lists every asset for which a consent decision has been recorded, shows the current state (granted, declined, or downloaded), and SHALL allow the user to revoke consent and delete the downloaded asset for any listed asset.
6. WHEN the user revokes consent and deletes a downloaded asset, THE System SHALL remove the asset bytes from local storage, SHALL update the recorded consent decision in the Local_Database, and SHALL treat the dependent feature as unavailable until the user consents again.
7. THE System SHALL NOT initiate any network request before displaying the Network_Consent_Dialog for the relevant asset, and SHALL NOT initiate any network request for an asset whose most recent recorded decision is "Decline" or "Revoked".
8. FOR ALL asset identifiers, recording a consent decision and then reading the decision SHALL return the recorded value (round-trip property for consent storage).

### Requirement 25: Tiered Natural-Language Routing (NL_Parser First)

**User Story:** As a user, I want most task capture to use a fast deterministic parser, so the app is instant and battery-friendly, and the LLM only runs when needed.

#### Acceptance Criteria

1. WHEN the user submits natural-language input through any entry point (Chat_Interface, Quick_Capture, Sharesheet), THE Task_Router SHALL first invoke the NL_Parser.
2. IF the NL_Parser returns a successful parse with confidence above a configured threshold (default 0.8), THEN THE Task_Router SHALL construct the action plan directly from the NL_Parser output and SHALL NOT invoke the LLM_Agent.
3. IF the NL_Parser fails or returns a parse with confidence at or below the configured threshold, THEN THE Task_Router SHALL invoke the LLM_Agent with the input and the structured context.
4. THE NL_Parser SHALL expose a "confidence" score between 0.0 and 1.0 derived from the presence of recognized date/time/tag/priority tokens and the coverage ratio of recognized tokens to total input tokens.
5. THE Task_Router SHALL expose metrics (visible in Settings → Diagnostics) for the percentage of requests handled at each tier over the last 30 days, stored only on device.
6. FOR ALL inputs that the NL_Parser handles successfully above threshold, the end-to-end latency from submission to action plan ready SHALL be under 50 milliseconds on a Samsung Galaxy S23 class device (no LLM involvement).
7. THE Task_Router SHALL be the single code path that may invoke the LLM_Agent for task creation, update, and deletion from natural language.
8. WHEN the user input matches any of the following patterns, THE Task_Router SHALL always escalate to the LLM_Agent and SHALL NOT short-circuit to an NL_Parser-only action plan: (a) multi-clause inputs joined by "and" or "then" that produce more than one action, (b) inputs containing bulk predicates such as "all", "every", "my meetings", or "my tasks", (c) inputs containing relative task references such as "the X" or "my X" (for example "the standup", "my review"), and (d) inputs containing relative-to-task temporal references such as "before X", "after X", or "at the same time as X". The NL_Parser MAY still be used for per-action field extraction within each expanded sub-action as the LLM_Agent produces them, but SHALL NOT solely produce the multi-action plan.

### Requirement 26: Few-Shot Examples and Optional Fine-Tuning Adapter

**User Story:** As a developer maintaining task-creation quality, I want the LLM_Agent to be biased toward producing canonical action plan JSON through bundled few-shot examples and an optional LoRA adapter, so that structured output stays reliable without any on-device training.

#### Acceptance Criteria

1. THE System SHALL bundle a Few_Shot_Examples JSON file in the APK / AAB containing at least 40 example pairs, covering (a) simple single-task create, (b) multi-task create in one utterance, (c) task update by title reference, (d) task delete by title reference, (e) habit create and log, (f) focus session start, and (g) ambiguous input disambiguation.
2. THE Few_Shot_Examples file SHALL conform to a documented JSON schema with fields {id, user_text, action_plan, tags}. THE System SHALL validate the file at application startup and SHALL refuse to start the LLM_Agent if validation fails, logging a diagnostic to Settings → Diagnostics.
3. THE LLM_Agent SHALL select in-context examples from the Few_Shot_Examples file using a deterministic similarity score against the current user input, computed entirely on-device.
4. THE System MAY bundle a LoRA_Adapter for the default base model (Gemma 3 1B Q4). IF a LoRA_Adapter is bundled AND the runtime supports adapter application for the loaded base model, THEN THE LLM_Agent SHALL apply the adapter at model load time. Otherwise THE LLM_Agent SHALL run the base model without an adapter.
5. THE System SHALL NOT perform any model training or fine-tuning on the device. Any fine-tuning SHALL be performed off-device by the developer, and the resulting adapter SHALL be bundled as an asset.
6. FOR ALL user inputs in the Few_Shot_Examples file, when replayed through the full Task_Router plus LLM_Agent pipeline with grammar-constrained decoding, the produced action plan SHALL be equivalent to the example's canonical action_plan (round-trip property for few-shot examples).

### Requirement 27: Multi-Action Plans, Entity Resolution, and Compositional Chat

**User Story:** As a user, I want to say things like "Move tomorrow's meetings to Thursday and add lunch with Priya at noon before the standup" in one message, so the assistant understands bulk changes, relative references, and multiple actions in a single turn.

#### Acceptance Criteria

1. THE Task_Router SHALL support single-turn inputs that contain any combination of: (a) multiple actions of different types (for example an update and a create in one message), (b) bulk predicates (for example "all my meetings", "everything tagged #work"), (c) relative date references ("tomorrow", "next Friday", "the 14th"), (d) relative task references ("the standup", "Priya's review", "the lunch I added"), and (e) relative-to-task temporal references ("before X", "after X", "at the same time as X", "30 min before X").
2. WHEN the input contains a bulk predicate, THE Entity_Resolver SHALL expand the predicate into the concrete set of matching Task and Event identifiers by applying the following deterministic rules:
   - "meetings" → Tasks with tag "#meeting" OR Events imported from .ics with DTSTART on the referenced date.
   - "work tasks" → Tasks with tag "#work".
   - "all tasks" → all Tasks (not Events) on the referenced date.
   - Generic tag reference "#X" → all items tagged "#X".
   - Custom predicate rules for other categories MAY be added in configuration, but the default rule set SHALL include at least the entries listed here.
3. WHEN the input contains a relative task reference (for example "the standup"), THE Entity_Resolver SHALL attempt to resolve it to exactly one Task or Event by applying the following priority order: (a) exact title match on the referenced date, (b) case-insensitive title contains the reference token, on the referenced date, (c) tag match (for example "#standup"), (d) most-recently-modified Task in the last 14 days whose title contains the reference. IF exactly one match is found after applying the rules, THE Entity_Resolver SHALL bind the reference to that identifier. IF more than one match is found or none, THE Entity_Resolver SHALL create an Ambiguity_Marker on the generated action.
4. WHEN the input contains a relative-to-task temporal reference ("before X", "after X", "at the same time as X"), THE LLM_Agent SHALL compute the start time for the new or moved task, using the identifier for X that the Entity_Resolver has already produced, by the following rules:
   - "before X" → THE System SHALL prefer the explicit time given in the user input if provided; otherwise default to 30 minutes before X's start time.
   - "after X" → 15 minutes after X's end time, or start time plus 30 minutes if X has no end time.
   - "at the same time as X" → copy X's start and end time.
   - IF the explicit time conflicts with the relative reference (for example "at noon before the standup" where the standup begins at 10:00), THEN THE System SHALL honor the explicit time, mark the action with an Ambiguity_Marker of type "constraint_violation", and surface the conflict in the Action_Plan_Review (see Requirement 28).
5. WHEN the input references "tomorrow's meetings", "next week's tasks", or similar date-windowed bulk phrases, THE Entity_Resolver SHALL resolve the date window first, then apply the predicate rule from acceptance criterion 2 over that window.
6. THE LLM_Agent SHALL emit Action_Plans that contain concrete Task or Event UUIDs in their `target_id` fields, not natural-language names. Natural-language names SHALL appear only in the `display_label` field of each action to help the user recognize items in the Action_Plan_Review.
7. THE Action_Plan JSON schema SHALL permit an ordered list of 1 to 50 actions in a single plan. IF a plan exceeds 50 actions, THEN THE System SHALL reject the plan with a descriptive error and THE Chat_Interface SHALL ask the user to narrow the scope.
8. FOR ALL inputs, the Task_Router pipeline SHALL be deterministic given the same Local_Database state and the same input, when the LLM_Agent is run with temperature zero and a fixed random seed. In production a small non-zero temperature MAY be used for more natural language, but the test harness SHALL run with temperature zero and a fixed seed (determinism invariant for testability).
9. WHEN the Entity_Resolver produces any Ambiguity_Marker on an action, THE Task_Router SHALL NOT apply the action plan automatically. THE Task_Router SHALL route the plan to the Action_Plan_Review surface (Requirement 28) for user disambiguation.
10. THE Entity_Resolver SHALL NOT issue any network call. All resolution SHALL use only the Local_Database and the current input context.

### Requirement 28: Action Plan Review Form

**User Story:** As a user, I want to see the exact list of actions the assistant will take, edit any of them if needed, resolve any ambiguous references, and then submit with one button, so that I am always in control.

#### Acceptance Criteria

1. WHEN a valid Action_Plan is produced by the Task_Router (whether via the NL_Parser fast path or the LLM_Agent escalation path), THE Chat_Interface SHALL route the plan to the Action_Plan_Review surface before any Task_Store mutation occurs. Plans that contain only read-only actions and no Ambiguity_Markers MAY be applied without review (per Requirement 9 acceptance criterion 3).
2. THE Action_Plan_Review SHALL render each action as an editable row showing at minimum: the `action_type` (create_task, update_task, delete_task, create_habit, log_habit, start_focus_session), the `display_label` summarizing the affected item, the final values for each editable field (title, start time, end time, date, tags, priority, recurrence), and any Ambiguity_Marker for that action with an affordance to resolve it.
3. WHEN an action has an Ambiguity_Marker referencing an unresolved entity (for example multiple candidates for "the standup"), THE Action_Plan_Review SHALL present a picker populated with the candidate Task and Event identifiers found by the Entity_Resolver, ordered by the priority rules from Requirement 27 acceptance criterion 3. The user SHALL select one candidate to resolve the marker. Applying the plan SHALL be blocked while any Ambiguity_Marker remains unresolved.
4. THE Action_Plan_Review SHALL allow the user to:
   (a) Edit any field on any action in place.
   (b) Toggle each action on or off (off-actions SHALL be skipped by apply).
   (c) Remove an action from the plan.
   (d) Add a new action to the plan from a template picker.
   (e) Reorder actions via drag-and-drop.
5. WHEN the user edits any field, THE System SHALL re-run action-plan validation (per Requirement 9 acceptance criterion 2) on the edited plan and SHALL highlight any newly-failing action without applying anything.
6. THE Action_Plan_Review SHALL provide two primary actions: "Apply" (or "Submit"; the label SHALL be configurable) and "Cancel". A "Save as draft" secondary affordance SHALL also be available; drafts SHALL persist in the Local_Database until the user discards them.
7. WHEN the user selects "Apply", THE Task_Store SHALL apply all enabled actions in order within a single transaction (per Requirement 9 acceptance criterion 4). IF any action fails, THEN THE Task_Store SHALL roll back all actions and THE Action_Plan_Review SHALL display the failing action highlighted, preserving the user's edits for correction.
8. WHEN the user selects "Cancel", THE System SHALL discard the pending Action_Plan without side effects.
9. THE Action_Plan_Review SHALL render every action and every editable field with full TalkBack content descriptions to comply with Requirement 20.
10. FOR ALL Action_Plans, the act of opening the Action_Plan_Review and pressing "Apply" without edits SHALL produce the same final Task_Store state as directly applying the validated plan (review pass-through invariant).
11. FOR ALL Action_Plans that the user edits and then applies, the final Task_Store state SHALL be equivalent to applying the edited plan directly via the same Task_Store.apply path (edit-fidelity invariant).
12. WHEN the Action_Plan_Review is open and the user sends a subsequent natural-language affirmative reply in the Chat_Interface ("go ahead", "yes", "submit", "do it", "apply"), THE Chat_Interface SHALL treat the reply as equivalent to pressing "Apply", provided no Ambiguity_Markers remain unresolved. IF Ambiguity_Markers remain, THEN THE Chat_Interface SHALL instruct the user to resolve them first and SHALL NOT apply the plan.
13. WHILE the Action_Plan_Review is open, THE LLM_Agent model SHALL remain loaded (warm) until the user Applies, Cancels, or the idle timeout fires (per Requirement 7 acceptance criterion 16).

### Requirement 29: Open-Source Distribution, Brand Identity, and Public Play Store Release

**User Story:** As the developer, I want to publish Aetheris Planner as a public, source-available GitHub project and eventually as a Google Play Store app that respects my offline-first principles, so that others can inspect and learn from the code while I retain control over commercial use.

This requirement is non-functional. It concerns brand identity, open-source distribution, and the conditional public Play Store release. The runtime offline-first properties enumerated in Requirement 5 remain the single source of truth; none of the acceptance criteria below weaken any of them.

#### Acceptance Criteria

1. THE System SHALL be identified publicly as "Aetheris Planner" with the tagline "Offline AI Planner for your Daily Life".
2. THE System SHALL use the Android `applicationId` `dev.aetheris.planner` and the Kotlin namespace `dev.aetheris.planner.*`.
3. THE source code SHALL be published in a public GitHub repository named `aetheris` under the PolyForm Noncommercial 1.0.0 license. The repository SHALL contain a LICENSE file with the full license text at the repository root.
4. THE repository SHALL include a NOTICE file listing the licenses of all bundled third-party dependencies (llama.cpp MIT, whisper.cpp MIT, MediaPipe Apache-2.0, SQLCipher BSD-3, Room Apache-2.0, Compose Apache-2.0, Kotlin Apache-2.0, and any additional dependencies added during implementation).
5. THE repository SHALL include a README that (a) explains the product, (b) links to the license and third-party attributions, (c) documents the brand etymology, (d) documents how to run the project locally, and (e) links to the Play Store listing once live.
6. THE repository SHALL include a SECURITY.md, CONTRIBUTING.md, and a CODE_OF_CONDUCT.md at the repository root.
7. THE repository SHALL include a privacy policy as a static page hosted via GitHub Pages (`/docs/privacy-policy.md` → served at `https://<user>.github.io/aetheris/privacy-policy`). The privacy policy SHALL declare that no user data is transmitted, stored, or processed off-device, and that no analytics, telemetry, crash reporting, or advertising SDKs are included. The Settings → About screen in the application SHALL link to this privacy policy URL.
8. THE repository SHALL have branch protection rules on the `main` branch requiring at least one passing CI run before merge, and disallowing force pushes.
9. WHEN the user elects to proceed with a public Play Store release (conditional on satisfaction with the app after Phase 6), THE System SHALL be published on the Google Play Store under the display name "Aetheris Planner" via a staged production rollout (10% → 25% → 50% → 100%).
10. FOR the Play Store listing, THE System's Data Safety declaration SHALL state that no data is collected, shared, or transmitted off-device.
11. THE Play Store listing SHALL include the privacy policy URL referenced in acceptance criterion 7.
12. THE Play Store application SHALL be enrolled in Google Play App Signing. The upload key SHALL be the existing release keystore; Google Play SHALL resign with the app signing key. The upload key alias SHALL be `aetheris-release`.
13. THE production release SHALL satisfy Google Play's target-SDK, 64-bit ABI, content rating, and Closed Testing prerequisite (20 testers for 14 days) requirements before the first production upload.
14. IF at any point after publication the user decides to withdraw the app from the Play Store, THEN THE System SHALL remain fully available as source code under its existing license on GitHub; no app functionality SHALL depend on the Play Store listing being present.

## Offline Capability Matrix

This matrix enumerates every requirement and its offline status. It is informative and derives from the acceptance criteria of Requirements 1–29. Requirement 5 (Offline-First Operation and Fully Local Data Storage) is the single source of truth for any network-consent UX; every entry below that mentions a consented download refers back to the Network_Consent_Dialog defined in Requirement 5 and concretized in Requirement 24.

Status values used below:

- **Fully offline** — works with zero network and zero asset download, out of the box.
- **Fully offline after bundled asset** — works with zero network; relies on an asset bundled inside the APK / AAB.
- **Fully offline after consented one-time download** — requires a user-consented, one-time asset download via the Model_Downloader; once the asset is present, subsequent use is zero network.
- **Not applicable** — no data or model dependencies.

| Requirement | Title | Offline Status |
| --- | --- | --- |
| 1 | Visual Day Timeline | Fully offline |
| 2 | Task Management | Fully offline |
| 3 | Recurring Tasks | Fully offline |
| 4 | Local Reminders and Notifications | Fully offline |
| 5 | Offline-First Operation and Fully Local Data Storage | Fully offline |
| 6 | Local Database Encryption | Fully offline |
| 7 | On-Device LLM Chat Assistant (Gemma 3 1B Q4_K_M — bundled minimum) | Fully offline after bundled asset |
| 7 | On-Device LLM Chat Assistant (Gemma 2 2B Q4_K_M — recommended, offered on first Chat open) | Fully offline after consented one-time download |
| 7 | On-Device LLM Chat Assistant (optional: Phi-3.5 Mini, Gemma 3 4B, Llama 3.2 3B, Qwen 2.5 3B) | Fully offline after consented one-time download |
| 8 | Natural-Language Task Parsing | Fully offline |
| 9 | LLM Action Plans and Validation | Fully offline |
| 10 | On-Device Speech Input (default whisper-tiny English) | Fully offline after bundled asset |
| 10 | On-Device Speech Input (optional whisper-base / whisper-small / multilingual) | Fully offline after consented one-time download |
| 11 | Habit Tracking | Fully offline |
| 12 | Focus Sessions | Fully offline |
| 13 | Daily Planning Ritual | Fully offline |
| 14 | Daily Shutdown Ritual | Fully offline |
| 15 | Quick Capture | Fully offline |
| 16 | iCalendar Import | Fully offline |
| 17 | Search | Fully offline |
| 18 | Home-Screen Widgets | Fully offline |
| 19 | Backup, Export, and Import | Fully offline |
| 20 | Accessibility and Internationalization | Fully offline |
| 21 | Permissions and Onboarding | Fully offline |
| 22 | Theming and Appearance | Fully offline |
| 23 | Performance and Resource Budgets | Fully offline |
| 24 | Network Consent and Reduced Functionality | Fully offline (the consent recording itself uses only the Local_Database; network I/O occurs only inside the Model_Downloader after consent) |
| 25 | Tiered Natural-Language Routing (NL_Parser First) | Fully offline |
| 26 | Few-Shot Examples (bundled) | Fully offline after bundled asset |
| 26 | Optional LoRA Adapter (when bundled) | Fully offline after bundled asset |
| 26 | Optional LoRA Adapter (when absent) | Not applicable |
| 27 | Multi-Action Plans, Entity Resolution, and Compositional Chat | Fully offline |
| 28 | Action Plan Review Form | Fully offline |
| 29 | Open-Source Distribution, Brand Identity, and Public Play Store Release | Fully offline (the Play Store release is a distribution concern, not a runtime concern) |

## Correctness Properties Summary (for Property-Based Testing)

The following properties, drawn from the acceptance criteria above, are suitable for property-based testing:

1. **Recurrence_Engine round-trip** (Req 3.5): `parse(print(rrule)) ≡ rrule` for all valid RRULEs.
2. **NL_Parser round-trip** (Req 8.6): `parse(print(task)) ≡ task` for all parsed Task candidates.
3. **NL_Parser determinism** (Req 8.7): `parse(s) == parse(s)` for all input strings.
4. **Task time invariant** (Req 2.3): `task.startTime ≤ task.endTime` whenever both are set.
5. **Reminder cancel-after-schedule idempotence** (Req 4.9): `state == cancel(schedule(state, r))`.
6. **Action plan idempotence under replay** (Req 9.5): `apply(apply(state, plan), plan) == apply(state, plan)`.
7. **Streak invariant** (Req 11.5): `0 ≤ currentStreak ≤ longestStreak`.
8. **Focus session time invariant** (Req 12.5): `activeDuration + pausedDuration == totalDuration`.
9. **Search subset property** (Req 17.3): `search(q, items) ⊆ items`.
10. **Search empty-query property** (Req 17.4): `search("", items) == ∅`.
11. **ICS round-trip** (Req 16.5): `parse(serialize(event)) ≡ event`.
12. **Backup round-trip** (Req 19.4): `import(export(db)) ≡ db`.
13. **Consent decision round-trip** (Req 24.8): `read(record(decision)) == decision` for all asset identifiers and decisions.
14. **Zero network at runtime** (Reqs 5.1, 5.4, 7.2, 10.5): For all user-initiated operations in the set {create Task, read Task, update Task, delete Task, chat with LLM_Agent, transcribe speech via Speech_Recognizer, run focus session, log Habit, import .ics from local storage, export backup to local storage, import backup from local storage}, the set of outbound network requests made by the application process SHALL be empty. This property is testable by running these operations against a stubbed network layer (for example an OkHttp Dispatcher or a SocketFactory override) that fails any outbound connection attempt, and asserting that no failure is triggered.
15. **LLM lifecycle idle-unload** (Req 7.16): `after Δt > idleTimeout with no inference, modelLoaded == false`. Testable with a deterministic clock fake driving the idle timer.
16. **Task_Router routing invariant** (Req 25 AC 2 and 3): `parserConfidence > threshold ⇒ ¬invokeLLM(input)`. Testable by varying NL_Parser confidence and asserting that the LLM_Agent is never invoked above threshold.
17. **Few-shot replay** (Req 26 AC 6): for every entry `e` in Few_Shot_Examples, `pipeline(e.user_text) ≡ e.action_plan`.
18. **Grammar-constrained output** (Req 9 AC 7 and 9): for all outputs emitted by the LLM_Agent, `validate(output, actionPlanSchema) == true OR output == conversationalFallback`. Testable by stubbing the LLM to produce arbitrary tokens and confirming the grammar constraint or the fallback holds.
19. **Entity_Resolver determinism** (Req 27 AC 8): for a fixed Local_Database state and input, `resolve(db, input) == resolve(db, input)`. Testable with a fixed seed, temperature zero, and a snapshot database.
20. **Ambiguity gating** (Req 27 AC 9, Req 28 AC 3): for all Action_Plans, `∃ action. action.ambiguity != null ⇒ ¬autoApply(plan)`. Testable by constructing plans with synthetic Ambiguity_Markers and asserting that apply is blocked.
21. **Review pass-through invariant** (Req 28 AC 10): `apply(reviewWithoutEdits(plan)) ≡ apply(plan)` for all valid Action_Plans.
22. **Review edit fidelity invariant** (Req 28 AC 11): `apply(review(plan).editedPlan) ≡ apply(editedPlan)` for all edit sequences produced in the review surface.
23. **Bounded action count** (Req 27 AC 7): `|plan.actions| ∈ [1, 50]` for every Action_Plan accepted by the validator.

These properties will inform the design phase's test strategy.
