# Performance review

The domain, codec, editor, validator, and macro limits are plain Kotlin. Toolbar persistence uses a single compact SharedPreferences document and `apply()` to avoid a blocking edit on the UI thread. Compose observes immutable repository state. Macro delays run in a coroutine and have bounded totals.

No profiling or large-configuration benchmark was run. The UI and memory behavior on low-end API 26 hardware remain unverified.
