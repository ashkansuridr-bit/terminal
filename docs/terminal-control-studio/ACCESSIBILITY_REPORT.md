# Accessibility review

The renderer attaches a content description to each key and appends active/locked modifier state. Buttons expose button semantics, disabled keys are disabled, and the keyboard affordance remains available independently of profile contents. Key targets render at 48dp height.

The macro editor's name and text inputs have localized accessibility names. The new device test is designed to exercise credential rejection, a disabled Create action and safe macro persistence, but has not run yet. Local `adb` exists; no local runnable emulator or attached device is available. CI runs API26/36 instrumentation; the latest completed run is [39059627211](https://github.com/ashkansuridr-bit/terminal/actions/runs/39059627211), with 448/456 tests passing and eight unrelated-to-macro test failures. TalkBack, font scaling, keyboard navigation and localized announcements have not been independently exercised. Status: runtime accessibility verification pending.
