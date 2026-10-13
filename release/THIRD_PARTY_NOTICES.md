# Third-party notices for release builds

Terminal SSH's own source is licensed under Apache License 2.0; see [`../LICENSE`](../LICENSE). Third-party components keep their own licences. The main dependency notices are also listed in [`../NOTICE.md`](../NOTICE.md).

## Android application dependencies

- `com.github.mwiede:jsch:2.28.6` — Revised BSD licence. JSch's upstream also publishes separate notices for JZlib (Revised BSD) and jBCrypt (ISC), as applicable to the resolved artifact.
- `org.connectbot:termlib:0.2.1` — Apache License 2.0. Its upstream identifies libvterm, licensed under MIT, as a terminal-parser dependency.
- `org.bouncycastle:bcprov-jdk18on:1.85` — MIT licence.
- AndroidX and Jetpack Compose — Apache License 2.0. Include the attribution material from the actual resolved artifacts and any transitive components shipped in the selected variant.

## Video assets distributed in the repository

The promotional MP4 files are separate media works and are not covered by the app's Apache licence. The video delivery disclosure records the available provenance and licence information for its Mixkit audio assets. The Vazirmatn typeface is under SIL Open Font License 1.1; its licence text is in [`../licenses/Vazirmatn-OFL.txt`](../licenses/Vazirmatn-OFL.txt).

Before publishing an APK/AAB, inspect that exact variant's resolved runtime dependency graph, verify the licences and notices against the included artifacts, and ship all required copyright and attribution text. This short list is not a substitute for an artifact-specific inventory.

No Termux application source code is vendored into this project.
