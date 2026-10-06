# TERMINAL SSH — AUTONOMOUS LOOP ENGINE MASTER PROMPT

## ROLE

تو به‌عنوان **Senior Android Engineer + Security Engineer + QA Engineer + Release Engineer + Product/UX Engineer** روی پروژه Terminal SSH کار می‌کنی.

این کار یک code-generation task ساده نیست.

تو مسئول **تحویل واقعی، تست‌شده، قابل build و قابل اثبات** هستی.

اصل اصلی:

> **Do not optimize for appearing finished. Optimize for actually being finished.**

هیچ موردی صرفاً با نوشتن کد Done محسوب نمی‌شود.

---

# EXECUTION MODE: LOOP ENGINE

برای کل این مأموریت از روش **LOOP ENGINE** استفاده کن.

چرخه اصلی:

`INSPECT → PLAN → IMPLEMENT → BUILD → TEST → VERIFY → REVIEW → FIX → RE-TEST → CONTINUE`

این چرخه باید تا زمانی ادامه پیدا کند که تمام شروط Definition of Done برآورده شوند.

اگر تست fail شد:

`FAIL → ROOT CAUSE → FIX → REGRESSION TEST → FULL RE-VERIFY`

اگر build fail شد:

`BUILD FAIL → DIAGNOSE → FIX → CLEAN BUILD → TEST`

اگر implementation ناقص بود:

`GAP → IMPLEMENT → TEST → VERIFY`

اگر یک تغییر باعث regression شد:

`REGRESSION → REVERT/REWORK → RETEST`

**هیچ failure شناخته‌شده‌ای را صرفاً document نکن و رد نشو.**

اگر قابل اصلاح است، اصلاحش کن.

---

# HARD RULES

## Rule 1 — No Fake Completion

تا زمانی که تمام موارد قابل انجام واقعاً انجام نشده‌اند، اعلام نکن:

- Done
- Complete
- Production Ready
- Fixed
- Finished
- Ready for Release

---

## Rule 2 — Evidence Required

برای هر موردی که Fixed اعلام می‌کنی باید حداقل یکی از این مدارک وجود داشته باشد:

- Unit Test
- Instrumentation Test
- Integration Test
- Build output
- Static verification
- Reproduction before / verification after
- Artifact inspection

عبارت‌هایی مثل:

`should work`

`likely fixed`

`probably`

برای Definition of Done قابل قبول نیستند.

---

## Rule 3 — Never Hide Failures

هیچ‌کدام از این موارد ممنوع است:

- حذف تست failing
- disable کردن تست
- `@Ignore`
- افزایش timeout برای مخفی‌کردن race
- catch کردن exception بدون handling
- `runCatching {}` برای پنهان‌کردن failure حساس
- تبدیل error به success
- حذف validation برای عبور تست
- hardcode کردن نتیجه تست
- fake/mock کردن چیزی که باید integration-tested باشد

---

## Rule 4 — Fix Root Causes

workaround فقط وقتی پذیرفته است که:

1. دلیل فنی مشخص داشته باشد.
2. regression test داشته باشد.
3. محدودیت آن document شود.

در غیر این صورت root cause را اصلاح کن.

---

# FIRST PHASE — REPOSITORY RECONNAISSANCE

قبل از تغییر کد، پروژه را کامل inspect کن.

بررسی کن:

- repository structure
- branches
- Gradle
- flavors
- buildTypes
- AndroidManifest
- Application
- ViewModels
- Session lifecycle
- SSH
- SFTP
- Vault
- AndroidKeyStore
- DocumentsProvider
- Foreground Service
- Terminal UI
- Settings
- Agent installer
- CI workflows
- tests
- scripts
- README
- CHANGELOG
- releases
- dependencies

بعد یک وضعیت baseline تهیه کن.

Baseline باید شامل این موارد باشد:

- build status
- unit test status
- instrumentation test status
- lint status
- known failures
- release artifact status
- signing status
- versionName
- versionCode

---

# BASELINE FIRST

قبل از اولین تغییر تا حد امکان این موارد را اجرا کن:

```bash
./gradlew clean
./gradlew testMarketDebugUnitTest
./gradlew testGplayDebugUnitTest
./gradlew lintMarketDebug
./gradlew lintGplayDebug
./gradlew assembleMarketDebug
./gradlew assembleGplayDebug

```

همچنین buildهای release/preview مربوط به پروژه را بررسی کن.

اگر instrumentation environment موجود است، instrumentation tests را نیز اجرا کن.

نتایج baseline را ثبت کن.

---

# PRIORITY MODEL

کارها را دقیقاً به این ترتیب انجام بده:

## P0

Data Loss + Security + Credential Safety

## P1

Lifecycle + Reliability + Release Safety

## P2

Correctness + Persistence + RTL

## P3

Maintenance + Dependencies

## P4

UI/UX Improvements

## P5

Competitive Features

تا P0/P1 پایدار نشده‌اند وارد feature expansion سنگین نشو.

---

# PHASE 1 — P0 DATA LOSS & SECURITY

## TASK 1 — LARGE TEXT EDITOR DATA LOSS

### Problem

Remote text editor ممکن است تنها حدود 512KB اول فایل را دریافت کند و سپس همان محتوای ناقص را روی فایل کامل overwrite کند.

این یک data-loss bug است.

### Required implementation

Edit path باید قادر به تشخیص truncation باشد.

حداقل:

```text
read MAX_EDIT_BYTES + 1

```

اگر فایل بزرگ‌تر از limit بود:

- Edit ممنوع
- Save ممنوع
- Read-only preview
- پیام واضح
- Download option
- Open externally option

API بهتر است چیزی شبیه این برگرداند:

```kotlin
data class TextLoadResult(
    val content: String,
    val truncated: Boolean,
    val totalSize: Long?
)

```

### Required test

فایل حداقل 2MB.

مراحل:

1. load
2. attempt edit
3. attempt save

Expected:

Original file must never be truncated.

---

## TASK 2 — FAIL-CLOSED UPLOAD CONFLICT DETECTION

`exists()` نباید تمام errorها را `false` تبدیل کند.

مدل مطلوب:

```kotlin
sealed interface RemoteExistence {
    data object Exists : RemoteExistence
    data object Missing : RemoteExistence
    data class Unknown(val cause: Throwable) : RemoteExistence
}

```

فقط:

`SSH_FX_NO_SUCH_FILE`

به Missing تبدیل شود.

این موارد:

- permission denied
- network loss
- timeout
- SSH failure
- generic SFTP failure

باید Unknown باشند.

در حالت Unknown:

- Upload متوقف شود.
- overwrite انجام نشود.
- error UI نمایش داده شود.

### Test Matrix

- file exists
- missing file
- permission denied
- disconnected session
- network error
- timeout

---

## TASK 3 — TRANSFER QUEUE STALL

Transfer scheduler نباید پس از پرشدن concurrency متوقف بماند.

scheduler را event-driven کن.

eventها:

- enqueue
- transfer completed
- transfer failed
- transfer paused
- resume
- retry
- network returned
- concurrency changed

پایان هر worker باید scheduler را signal کند.

### Mandatory test

Queue:

```text
30 small files
maxConcurrent = 3

```

بدون هیچ interaction بیرونی:

```text
30/30 must reach terminal state

```

هیچ QUEUED stuck قابل قبول نیست.

---

## TASK 4 — API KEY MUST NOT ENTER SHELL HISTORY

secret نباید در command string embed شود.

ممنوع:

```bash
export API_KEY='SECRET'

```

به‌خصوص reliance روی:

```text
HISTCONTROL
ignorespace
history -d
HISTCMD

```

قابل قبول نیست.

راه‌حل باید secret را از command channel جدا کند.

ممکن است از:

- stdin
- temporary non-persistent stream
- equivalent secure channel

استفاده شود.

### Requirements

secret نباید در:

- shell history
- terminal output
- logs
- exception message
- analytics
- command preview

ظاهر شود.

بعد از استفاده:

`ByteArray` / `CharArray`

wipe شوند.

### Test Matrix

- Bash
- Zsh
- no HISTCONTROL
- enabled history
- disabled history

---

# PHASE 2 — P1 RELIABILITY

## TASK 5 — INSTALL SCRIPT CLAIM

اگر UI می‌گوید script قبل از اجرا دیده می‌شود، این ادعا باید واقعی باشد.

Preferred architecture:

```text
DOWNLOAD
↓
VERIFY SOURCE
↓
CALCULATE HASH
↓
DISPLAY SCRIPT
↓
USER CONFIRMATION
↓
EXECUTE

```

اگر این architecture اجرا نمی‌شود، wording README/UI را اصلاح کن.

هر claim باید دقیقاً مطابق behavior باشد.

---

# TASK 6 — SESSION LIFECYCLE OWNERSHIP

هیچ component نباید مستقیماً:

```kotlin
SessionRegistry.closeAll()

```

را بدون cleanup کامل اجرا کند.

یک owner واحد ایجاد کن، مثلاً:

```text
SessionLifecycleManager

```

API:

```text
closeSession()
closeAllSessions()

```

ترتیب teardown:

```text
1 cancel transfer
2 persist queue
3 unregister TransferCoordinator
4 close SFTP channels
5 close SftpController
6 disconnect SSH
7 destroy session resources
8 remove registry entry
9 update foreground service

```

تمام مسیرها باید از manager عبور کنند:

- MainActivity
- ForegroundService
- timeout
- Disconnect All
- manual close
- task finishing

Regression test اضافه کن.

---

# TASK 7 — DOCUMENT PROVIDER PER-OPEN STAGING

staging file مشترک ممنوع.

برای هر open:

```text
saf-{session}-{uuid}-{safeName}

```

ایجاد شود.

Concurrent editors نباید cache مشترک داشته باشند.

### Test

دو caller همزمان یک remote document را باز کنند.

تغییرات staging نباید روی هم overwrite شوند.

---

# TASK 8 — DOCUMENT PROVIDER FAILED SAVE RECOVERY

اگر remote upload هنگام close شکست خورد:

ممنوع است:

```text
silently delete staging file

```

Required behavior:

- حفظ staging
- recovery state
- notification
- Retry
- Failed Save UI
- path/server info
- timestamp

کاربر نباید تصور کند فایل remote ذخیره شده است.

---

# TASK 9 — IS CHILD DOCUMENT PATH BOUNDARY

این غلط است:

```kotlin
child.startsWith(parent)

```

چون:

```text
/a
/abc

```

اشتباه match می‌شوند.

boundary-safe comparison پیاده‌سازی کن.

Test cases:

```text
/a -> /a/file = true
/a -> /abc = false
/ -> /anything = true
/a/b -> /a/bc = false

```

---

# TASK 10 — SSH KEY DEPENDENCY SAFETY

قبل از Delete key:

تمام host dependencyها پیدا شوند.

اگر host استفاده می‌کند:

direct delete ممنوع.

UI باید لیست dependency بدهد.

Actions:

- Cancel
- Replace key
- Change auth method
- Unlink affected hosts

Test required.

---

# TASK 11 — ORPHAN VAULT SECRETS

تغییر credential نباید secret قبلی را orphan کند.

Cases:

```text
password → password
password → key
key → password
passphrase → new passphrase

```

Sequence:

```text
write new secret
↓
persist new metadata
↓
verify success
↓
delete previous secret

```

اگر metadata save fail شد rollback انجام شود.

---

# TASK 12 — STREAMING SECRET MASKING

SecretScanner نباید فقط chunk مستقل را scan کند.

Implement:

- incremental UTF-8 decoder
- rolling overlap
- bounded buffer
- cross-chunk detection

Example:

```text
chunk1: sk-proj-ab
chunk2: cdefgh...

```

باید secret کامل mask شود.

Regression test ضروری.

---

# PHASE 3 — RELEASE SAFETY

# TASK 13 — FAIL-CLOSED RELEASE PIPELINE

Production publish graph:

```text
VERIFY
  ↓
BUILD MARKET RELEASE
  ↓
VERIFY ARTIFACTS
  ↓
PUBLISH

```

Publish نباید قبل از build نهایی اجرا شود.

اگر required artifact موجود نیست:

```text
FAIL

```

ممنوع:

- fallback به debug APK
- publish ناقص
- continue-on-error برای artifact الزامی

---

# TASK 14 — INSTRUMENTATION AS RELEASE GATE

Release gate باید حداقل critical instrumentation را اجرا کند.

Target matrix:

```text
API 26
API 30
API 34
API 36

```

در صورت محدودیت CI، حداقل API 26 + API 36 اجباری باشند و matrix توسعه یابد.

Critical suites:

- Terminal keyboard
- RTL
- DocumentsProvider
- AndroidKeyStore
- key generation
- biometric
- SFTP persistence
- transfer recovery

Critical failure = release blocked.

---

# PHASE 4 — RTL / TERMINAL

# TASK 15 — TERMINAL TOOLBAR

مشکل toolbar را root-cause fix کن.

Preferred:

```text
LazyRow

```

Requirements:

- proper semantics
- actual 48dp touch target
- RTL
- LTR
- IME opened
- IME closed
- small phone
- large screen

Test all.

---

# TASK 16 — ARABIC RTL

ممنوع:

```kotlin
language == "fa"

```

برای تشخیص RTL.

Direction باید locale-aware باشد.

حداقل:

```text
FA → RTL
AR → RTL
EN → LTR
FR → LTR
ES → LTR
RU → LTR

```

Instrumentation یا Compose test اضافه کن.

---

# PHASE 5 — SYNC / PERSISTENCE

# TASK 17 — SYNC CORRECTNESS

فقط size کافی نیست.

Modes:

## FAST

```text
path
size
mtime

```

## SAFE

```text
path
size
mtime
hash suspicious files

```

## VERIFY ALL

```text
SHA-256

```

Default mode باید balance خوبی بین correctness و performance داشته باشد.

Test:

دو فایل با size برابر ولی content متفاوت.

نباید incorrectly identical تشخیص داده شوند.

---

# TASK 18 — PERSIST BOOKMARKS AND SYNC PRESETS

بعد از:

- process death
- app restart
- reboot

اطلاعات باید باقی بمانند.

ذخیره persistent طراحی کن.

Migration compatibility را رعایت کن.

---

# PHASE 6 — PROJECT MAINTENANCE

# TASK 19 — README / RELEASE TRUTHFULNESS

README را با کد واقعی sync کن.

بررسی:

- repository link
- release link
- APK size
- test count
- versions
- signing
- flavors
- jump host
- port forwarding
- SFTP capabilities
- unsupported features

هیچ claim اشتباه باقی نماند.

---

# TASK 20 — DEPENDENCY UPDATE

Dependencyها را گروه‌بندی کن.

مثلاً:

```text
AndroidX Core
Compose
Lifecycle
Activity
Credentials
Kotlin/Coroutines
Crypto/SSH

```

برای هر batch:

```text
UPDATE
↓
BUILD
↓
UNIT TEST
↓
LINT
↓
INSTRUMENTATION
↓
MINIFIED BUILD

```

اگر breaking change مشاهده شد root cause را حل کن.

---

# UI/UX PHASE

پس از تثبیت P0/P1 موارد UI/UX زیر را بررسی و پیاده‌سازی کن.

هر feature باید:

```text
discoverable
fast
accessible
RTL-safe
phone-safe
tablet-safe where relevant

```

باشد.

---

## UX-01 — QUICK CONNECT

Input:

```text
user@server:port

```

parse خودکار:

- username
- host
- port

---

## UX-02 — COMMAND PALETTE

مشابه VS Code.

Actions:

- Connect
- Disconnect
- SFTP
- Snippets
- Settings
- Keys
- Port Forward
- Jump Host
- Clear
- Agents

Searchable.

---

## UX-03 — SERVER HEALTH CARD

نمایش سبک:

- latency
- connection age
- reconnect count
- transfer speed
- connection state

بدون polling غیرضروری.

---

## UX-04 — ENVIRONMENT BADGE

```text
PROD
STAGE
DEV

```

PROD باید واضح و همیشه قابل تشخیص باشد.

---

## UX-05 — PRODUCTION SESSION GUARD

در PROD:

```text
PRODUCTION SERVER

```

هشدار destructive command شدیدتر باشد.

---

## UX-06 — SESSION RESTORE CENTER

نمایش:

- previous sessions
- pending transfers
- disconnected sessions
- recoverable work

---

## UX-07 — MULTI SESSION OVERVIEW

نمایش همه sessionها:

- host
- status
- latency
- uptime
- transfer activity

---

## UX-08 — SPLIT TERMINAL

Tablet/foldable:

دو terminal همزمان.

Phone landscape:

optional split.

---

## UX-09 — SEARCH TERMINAL OUTPUT

Modes:

- exact
- regex
- case-sensitive

با highlight و next/previous.

---

## UX-10 — TERMINAL SCROLL MARKERS

Markers:

- search
- errors
- warnings
- command boundaries

---

## UX-11 — SEARCHABLE COMMAND HISTORY

فقط commandها.

Secret نباید وارد history UI شود.

---

## UX-12 — SMART SNIPPETS

Example:

```text
docker logs {{container}}

```

placeholder form قبل از insert/run.

---

## UX-13 — SNIPPET CATEGORIES

- Docker
- Git
- Linux
- DB
- Network
- Deploy
- Personal

---

## UX-14 — SAFE MULTILINE PASTE

قبل از paste:

```text
12 commands detected

```

Actions:

- Review
- Edit
- Send
- Cancel

---

## UX-15 — DANGEROUS PASTE DETECTION

تشخیص:

- rm
- mkfs
- DROP
- shutdown
- chmod 777
- force push

بدون false-positive آزاردهنده.

---

## UX-16 — GLOBAL TRANSFER CENTER

Tabs:

```text
Active
Queued
Paused
Failed
Completed

```

---

## UX-17 — TRANSFER RECOVERY

Actions:

- Retry
- Retry all
- Change destination
- Reconnect and retry

---

## UX-18 — BANDWIDTH PROFILES

- Unlimited
- Wi-Fi
- Mobile Saver
- Custom

---

## UX-19 — SMART FILE PREVIEW

- Text
- JSON
- YAML
- Markdown
- Image
- Logs
- Certificates

برای فایل بزرگ فقط range/window بخوان.

---

## UX-20 — FILE DIFF BEFORE SAVE

نمایش:

- added
- removed
- changed

قبل از write remote.

---

## UX-21 — GIT AWARENESS

اگر current path Git repository بود:

- branch
- dirty status
- changed count

نمایش داده شود.

هیچ operation خودکار Git بدون درخواست کاربر انجام نشود.

---

## UX-22 — HOST QUICK ACTIONS

برای هر host:

- logs
- status
- restart service
- deploy status

قابل customize.

---

## UX-23 — HOME DASHBOARD

Sections:

- Favorites
- Recent Hosts
- Active Sessions
- Pending Transfers
- Recent Errors

---

## UX-24 — CONNECTION TIMELINE

مثال:

```text
12:01 Connected
12:43 Network Lost
12:43 Reconnecting
12:44 Connected

```

---

## UX-25 — ACTIONABLE EMPTY STATES

هر empty state باید next action پیشنهاد دهد.

---

## UX-26 — CUSTOMIZABLE KEYBOARD TOOLBAR

قابل reorder:

- Ctrl
- Alt
- Esc
- Tab
- Home
- End
- PgUp
- PgDn

Custom keys نیز پشتیبانی شوند.

---

## UX-27 — ONE-HANDED MODE

Options:

- Normal
- Left-handed
- Right-handed

---

## UX-28 — TABLET/FOLDABLE WORKSPACE

Desktop-like layout:

```text
Hosts | Terminal | Files

```

---

## UX-29 — FIRST RUN SETUP

فقط:

1. Language
2. Security/lock
3. First server

Onboarding طولانی نساز.

---

## UX-30 — CONTEXT-AWARE ACTION BAR

Terminal:

- Paste
- Search
- Snippet
- Clear

SFTP:

- Upload
- Folder
- Sort
- Select

Host:

- Connect
- Edit
- Duplicate
- Favorite

---

# COMPETITIVE FEATURE BACKLOG

بعد از stabilization:

- Mosh
- Local forwarding
- Remote forwarding
- Dynamic SOCKS
- ProxyJump chains
- Split terminal
- Search history
- SSH Agent
- Custom keyboard
- host grouping
- tags
- quick actions
- Git workspace
- reconnect timeline
- transfer recovery
- server-to-server transfer
- safe remote editor
- tmux integration
- command palette
- tablet workspace
- shortcuts/widgets

قبل از پیاده‌سازی هرکدام بررسی کن آیا feature مشابه از قبل وجود دارد.

duplicate implementation نساز.

---

# BUILD ARCHITECTURE

دو codebase ایجاد نکن.

تمام artifactها باید از **یک commit** ساخته شوند.

---

# BUILD A — COMPLETE PREVIEW

Purpose:

Internal/testing complete version.

Configuration:

```text
Build Type: preview
Application ID: app.terminalssh.secure.preview
Signing: Debug/Test certificate
Installable: Yes
Production: No

```

Filename:

```text
TerminalSSH-X.Y.Z-complete-preview.apk

```

Requirements:

- minification ON
- resource shrinking ON
- release-like behavior
- separate package
- separate AndroidKeyStore vault
- install alongside production

---

# BUILD B — MARKET UNSIGNED

Purpose:

Artifact مخصوص signing/market pipeline.

Configuration:

```text
Flavor: market
Build Type: release
Application ID: app.terminalssh.secure
Debug Signing: FORBIDDEN
Production Signing: absent if key unavailable

```

Expected artifacts:

```text
TerminalSSH-X.Y.Z-market-UNSIGNED.apk
TerminalSSH-X.Y.Z-market-UNSIGNED.aab

```

اگر production key وجود ندارد artifact باید واقعاً UNSIGNED باشد.

هیچ debug certificate روی package production مجاز نیست.

---

# RELEASE ARTIFACT MANIFEST

برای هر release بساز:

```text
ARTIFACTS.md

```

Example:

```text
COMPLETE PREVIEW

Package:
app.terminalssh.secure.preview

Signed:
Debug/Test certificate

Installable:
Yes

Production:
No

```

و:

```text
MARKET RELEASE

Package:
app.terminalssh.secure

Signed:
No

Installable:
No until production signing

Market Candidate:
Yes after signing

```

---

# SHA256

برای تمام artifacts ایجاد کن:

```text
SHA256SUMS.txt

```

CI باید hash را verify کند.

---

# GIT / RELEASE STRUCTURE

Suggested branch:

```text
release/0.7.0

```

Suggested tag:

```text
v0.7.0-rc1

```

هر دو artifact باید از همان commit/tag ساخته شوند.

Binaryهای بزرگ را معمولاً داخل normal Git history قرار نده.

Preferred:

```text
GitHub Releases

```

Repository شامل:

- source
- workflows
- Gradle
- release notes
- ARTIFACTS.md
- SHA256SUMS.txt
- CHANGELOG

اگر repository policy الزام به binary storage دارد:

Git LFS استفاده شود.

---

# CI PIPELINE TARGET

Pipeline:

```text
SOURCE AUDIT
      ↓
UNIT TESTS
      ↓
LINT
      ↓
DEBUG BUILDS
      ↓
PREVIEW BUILD
      ↓
INSTRUMENTATION
      ↓
MARKET UNSIGNED
      ↓
ARTIFACT VALIDATION
      ↓
SHA256
      ↓
RELEASE

```

Release تنها در صورت سبز بودن تمام critical gateها انجام شود.

---

# REQUIRED RELEASE GATES

هیچ release تا عبور از همه این موارد ساخته نشود:

- JVM tests pass
- instrumentation pass
- lint no blocker
- preview builds
- market release builds
- minified build tested
- FA RTL tested
- AR RTL tested
- keyboard + IME tested
- 30-file transfer test
- transfer resume test
- process-death recovery test
- concurrent DocumentsProvider test
- large file editing test
- upload conflict error test
- host-key verification test
- Vault instrumentation
- backup disabled verified
- cleartext disabled verified
- package id verified
- signing certificate/status verified
- versionName verified
- versionCode verified
- SHA256 generated
- artifact names verified
- README updated
- CHANGELOG updated
- ARTIFACTS.md updated

---

# DEFINITION OF DONE FOR EVERY TASK

هیچ task با implementation تنها Done نیست.

Done requires:

```text
[ ] implementation
[ ] unit tests
[ ] integration test where applicable
[ ] instrumentation where applicable
[ ] negative/failure tests
[ ] regression test
[ ] loading state
[ ] error state
[ ] FA localization
[ ] EN localization
[ ] AR localization where applicable
[ ] accessibility
[ ] RTL check
[ ] docs
[ ] changelog
[ ] no market regression
[ ] no gplay regression
[ ] minified build verified

```

---

# PER-TASK LOOP

برای هر task این الگوریتم را اجرا کن:

```text
1. Reproduce current bug
2. Write or identify failing test
3. Inspect root cause
4. Design minimal robust fix
5. Implement
6. Run focused tests
7. Fix failures
8. Run module tests
9. Run regression tests
10. Run build
11. Review diff
12. Check security implications
13. Check lifecycle implications
14. Check RTL/accessibility
15. Mark task complete only with evidence
16. Proceed to next task

```

---

# CONTINUOUS SELF-REVIEW LOOP

پس از هر 3 تا 5 task:

دوباره کل تغییرات را review کن.

سؤال‌ها:

```text
Did we introduce data loss?
Did we weaken authentication?
Did we weaken host verification?
Did we leak credentials?
Did we introduce race conditions?
Did we break process-death recovery?
Did we break market flavor?
Did we break gplay flavor?
Did we break RTL?
Did we break API 26?
Did minification break reflection/runtime behavior?

```

اگر جواب هرکدام uncertain بود:

verify کن.

guess نزن.

---

# ARCHITECTURE RULE

هنگام fix کردن مشکلات، از اضافه‌کردن architecture غیرضروری خودداری کن.

اما duplication حساس را نیز حفظ نکن.

هدف:

```text
simple
testable
explicit ownership
fail-closed
recoverable

```

باشد.

---

# SECURITY PRINCIPLES

همیشه:

```text
Fail Closed
Least Privilege
No Secret Strings
No Silent Failure
Explicit Trust
Recoverable Writes
Atomic Metadata Changes

```

رعایت شود.

---

# DATA LOSS PRINCIPLE

هر عملیاتی که remote data را overwrite/delete می‌کند باید بررسی شود.

در عملیات حساس ترجیح:

```text
VALIDATE
↓
PREPARE
↓
VERIFY
↓
WRITE
↓
VERIFY
↓
COMMIT/CLEANUP

```

در صورت failure امکان recovery حفظ شود.

---

# FINAL PROJECT-WIDE VERIFICATION LOOP

بعد از اتمام همه taskها:

## LOOP A

```text
clean build
unit tests
lint

```

اگر fail:

fix و LOOP A دوباره.

## LOOP B

```text
instrumentation
critical UI tests
RTL tests

```

اگر fail:

fix و LOOP A + B دوباره.

## LOOP C

ساخت:

```text
complete preview
market unsigned APK
market unsigned AAB

```

artifactها را inspect کن.

بررسی:

- package ID
- version
- debug/release flag
- signature
- file names

اگر mismatch:

fix و از LOOP A شروع کن.

## LOOP D

Security regression:

- vault
- host key
- secrets
- backup
- cleartext
- provider permissions
- credentials

اگر fail:

fix و تمام loopها دوباره.

---

# FINAL ADVERSARIAL REVIEW

قبل از اعلام پایان پروژه، مثل reviewer مخالف عمل کن.

سعی کن featureها را بشکنی.

حداقل این سناریوها را بررسی کن:

```text
network drops during upload
network drops during download
process dies during transfer
remote file changes during resume
remote file changes while editing
permission denied during stat
permission denied during save
server disconnect during save
two editors open same remote file
host key changes
private key deleted while referenced
Arabic locale
Persian locale
IME open on small phone
30+ queued files
large text file
large image
invalid settings import
missing SAF permission
unknown SFTP error
production signing key missing

```

هر مشکل reproducible که پیدا کردی باید به loop fix برگردد.

---

# NO PREMATURE DELIVERY

تا زمانی که این شرایط برقرار نشده، خروجی نهایی تحویل نده:

```text
NO KNOWN P0
NO KNOWN P1
ALL CRITICAL TESTS PASS
ALL REQUIRED BUILDS PASS
ARTIFACTS VERIFIED
DOCUMENTATION MATCHES REALITY

```

اگر چیزی واقعاً به دلیل محدودیت محیط قابل تست نیست:

1. دقیقاً اعلام کن چه چیزی قابل اجرا نبود.
2. کد مربوط را fake-pass نکن.
3. برای آن test/manual verification procedure بساز.
4. آن مورد را «Verified» اعلام نکن.

---

# FINAL REPORT FORMAT

فقط بعد از پایان loopها گزارش نهایی بده.

گزارش باید شامل:

## 1. Summary

چه چیزهایی انجام شد.

## 2. Fixed Bugs

برای هر bug:

```text
Problem
Root Cause
Fix
Files Changed
Tests
Evidence

```

## 3. UI/UX Implemented

لیست قابلیت‌های UI/UX اجراشده.

## 4. Deferred Items

فقط مواردی که واقعاً خارج از scope یا به dependency بیرونی وابسته‌اند.

## 5. Tests

مثلاً:

```text
JVM: X passed
Instrumentation: X passed
Lint: PASS

```

هیچ عددی حدس زده نشود.

## 6. Builds

```text
Complete Preview: PASS
Market Unsigned APK: PASS
Market Unsigned AAB: PASS

```

## 7. Artifact Verification

برای هر artifact:

```text
Filename
Package ID
Version
Signing status
SHA256
Size

```

## 8. Remaining Known Issues

اگر صفر است:

```text
No known P0/P1 issues after executed test coverage.

```

نه:

```text
No bugs exist.

```

چون چیزی که تست نشده را نمی‌توان قطعی دانست.

## 9. Git State

گزارش:

```text
Branch
Commit SHA
Tag
Dirty/Clean

```

## 10. Release Readiness

فقط بر اساس evidence.

---

# IMPORTANT BEHAVIOR

در طول کار برای permission گرفتن بابت هر fix کوچک متوقف نشو.

اگر راه‌حل مشخص است، انجامش بده.

اگر implementation اول شکست خورد:

راه‌حل دوم را امتحان کن.

اگر test failure پیدا شد:

بررسی و اصلاح کن.

اگر build failure پیدا شد:

بررسی و اصلاح کن.

اگر architecture مشکل داشت:

refactor محدود و تست‌پذیر انجام بده.

کار را صرفاً به علت بزرگ‌شدن task متوقف نکن.

---

# PROJECT SUCCESS CRITERIA

Terminal SSH باید در این سه محور قوی شود:

## 1. Provable Security

نه claim امنیتی؛ امنیتی که test و inspect شود.

## 2. Mobile-First Developer Workflow

کاری که روی desktop چند پنجره لازم دارد، روی گوشی سریع انجام شود.

## 3. Reliability

قطع شبکه، process death، concurrent edit، transfer resume و خطاهای server نباید باعث data loss شوند.

---

# FINAL COMMAND

اکنون repository را از ابتدا inspect کن.

Baseline را بساز.

سپس P0 را شروع کن.

هر task را با Loop Engine تا verification واقعی کامل کن.

بعد وارد P1، سپس RTL/Persistence/Maintenance و در نهایت UI/UX شو.

هر بار که failure پیدا شد به loop اصلاح برگرد.

**هیچ مورد را صرفاً به این دلیل که کد آن نوشته شده Complete حساب نکن.**

**هیچ test failing شناخته‌شده‌ای را مخفی نکن.**

**هیچ artifact را بدون بررسی package/signing/version منتشر نکن.**

**Complete Preview و Market Unsigned باید از یک commit واحد ساخته شوند.**

**Market Unsigned نباید با debug key امضا شود.**

**تا زمانی که معیارهای Definition of Done و Release Gate واقعاً پاس نشده‌اند، کار را پایان‌یافته اعلام نکن.**