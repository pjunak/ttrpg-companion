# TTRPG Companion

Native Android app for TTRPG Codex campaign sites. The host
([`ttrpg-codex`](https://github.com/pjunak/ttrpg-codex)) owns every campaign
record; the app signs in with a site's password, reads the role-projected
campaign, keeps a copy for offline reading and opens the site's own web pages
for anything without a native screen. Why it exists and what it will cover is
in the host's [ADR-0008](https://github.com/pjunak/ttrpg-codex/blob/main/docs/decisions/0008-android-companion-app.md);
its tasks live in the host's
[backlog](https://github.com/pjunak/ttrpg-codex/blob/main/docs/BACKLOG.md).

This is a personal project. Keep campaign data and sessions safe, then favour
simple, clear code over extra hardening or process.

## Read by task

- [README.md](README.md) for setup, testing against a local host and releases.
- Before changing a host call: the host's
  [client API](https://github.com/pjunak/ttrpg-codex/blob/main/docs/reference/CLIENT_API.md)
  lists every route this app may use, including add-on services by contract
  (host 2.2.0 and newer).
- Record pages, subtitle and article fields follow the host's
  `frontend/src/app/routes.ts` and `campaign-projection.ts`; the dashboard,
  facts and settings lists follow `campaign-projection.ts`,
  `campaign-party.ts` and the record views. `core-model` turns them into
  `CampaignOverview`, `RecordDetails` and `CampaignSettings`.

## Commands

Gradle needs JDK 17+; Android Studio's bundled JBR works.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug test lintDebug
.\gradlew.bat :core-model:test :core-network:testDebugUnitTest   # focused
```

## Architecture

```text
app/             Compose UI, navigation, ViewModels, theme, live updates
core-model/      Pure Kotlin: host wire formats, record pages, wiki links, search
core-network/    OkHttp client and per-site cookies, host client, sites, campaign cache, events
feature-update/  GitHub release check, verified download, installer handoff
```

Dependencies point inward: `app` uses everything, `core-network` uses
`core-model`, and `core-model` has no Android dependencies.

## Host contract

- Routes: `GET /api/version` (major version 2), `GET /api/auth`,
  `POST /api/login`, `POST /api/logout`, `GET /api/campaign`
  (`campaign-data.v1`), `GET /api/events` (SSE with `Last-Event-ID`),
  `GET /api/media/{blob}` and, for session notes (events only),
  `POST /api/campaign/transactions`. Anything else opens in the signed-in web
  view.
- Writes are online only. They change only the fields the editor shows,
  merge with changes made elsewhere and ask before overwriting the same field
  (`core-model` `SessionNotes.kt`, `core-network` `EventWriter`).
- The host decides roles and projections. The app never shows data the
  session's projection lacks and never asks for a role.
- Find add-on features by service contract and version through
  `POST /api/services/connect` and `/call`, never by add-on ID. Optional
  features hide themselves when no provider answers or the host is older than
  2.2.0 (`405`).
- A wire change needs both sides checked: the host's tests and this app's
  model and client tests.

## Conventions

- Kotlin, Compose Material 3, Hilt, coroutines and Flow, versions from
  `gradle/libs.versions.toml` (kept in step with Baton where they overlap).
- ViewModels own orchestration; composables render state and send events.
- Colours, spacing and typography come from `ui/theme`; screens use shared
  pieces from `ui/components` instead of local styling.
- User-visible text lives in `res/values/strings.xml` with a Czech copy in
  `res/values-cs/`; add both. A site's screens follow its theme and language
  through `SiteScope`; icon-only buttons have content descriptions.
- Titles use Cinzel and articles Lora (`res/font`, licences in
  `assets/licenses`), as on the website.
- One OkHttp client and cookie jar serve REST, images and the event stream.
  Session cookies are stored encrypted per site origin; passwords are never
  stored.
- HTTPS only. Debug builds also accept plain HTTP to local development hosts.

## Release

A push to `main` builds, tests, lints, signs and publishes the APK as GitHub
release `v0.1.<build>` (see [CI](.github/workflows/ci.yml)); the in-app
updater offers it and Android's installer asks the user. The build number is
the workflow run number and must only grow. Signing needs the
`KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` and
`KEYSTORE_KEY_PASSWORD` secrets; never commit a keystore or change the signing
key without the owner. Commit locally after validation; pushing and publishing
need the owner's explicit approval.

## Testing

Models, wire parsing and the host client have JVM tests (the client against
MockWebServer). For UI or protocol changes, also run the app on an emulator
against a throwaway local host (README); never point it at a live site's data
during development.
