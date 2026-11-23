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
- Maps follow the host's `docs/reference/MAPS.md` and
  `frontend/src/app/campaign-map.ts` (which pins a map shows, marker sizes
  and the hash that picks random marker images, the event trail);
  `core-model` `CampaignMap.kt`, `MapTiles.kt` and `MapViewport.kt` mirror
  them.
- Record pages, list subtitles, excerpts and icons follow the host's
  `frontend/src/app/routes.ts` and `campaign-projection.ts`; the dashboard
  and settings lists follow `campaign-projection.ts`, `campaign-party.ts`
  and the record views; record facts follow `codex-record-page.ts` and, for
  characters, `codex-character-profile.ts`. `core-model` turns them into
  `CampaignPages`, `CampaignOverview`, `RecordDetails` and
  `CampaignSettings`.
- Public/DM twins are grouped as `campaign-twins.ts` does
  (`CampaignTwins.kt`), search follows `campaign-search.ts`
  (`CampaignSearch.kt`) and sessions follow `campaign-timeline.ts`
  (`SessionNotes.kt`).

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
core-model/      Pure Kotlin: host wire formats, record pages, wiki links, search, map math
core-network/    OkHttp client and per-site cookies, host client, sites, campaign and sheet copies, maps, events, add-on services
feature-update/  GitHub release check, verified download, installer handoff
```

Dependencies point inward: `app` uses everything, `core-network` uses
`core-model`, and `core-model` has no Android dependencies.

## Host contract

- Routes: `GET /api/version` (major version 2), `GET /api/auth`,
  `POST /api/login`, `POST /api/logout`, `GET /api/campaign`
  (`campaign-data.v1`), `GET /api/events` (SSE with `Last-Event-ID`),
  `GET /api/media/{blob}`, for maps `GET /api/media/latest/world-map/main`,
  `GET /api/media/{blob}/tiles/v1/manifest` (`map-tiles.v1`) and
  `.../tiles/v1/{level}/{x}/{y}`, and, for session notes (events only),
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

## Character sheet

- Service `dnd5e.character` 4.x (requests `character.v3`, answers
  `character-response.v3`), found by contract through `CharacterService`,
  never by the Character Sheets add-on's ID; the sheet hides itself without a
  provider.
- The readers are hand-written: `core-model` `character/`
  (`CharacterAnswer`, `CharacterSheet`, `CharacterRequest`,
  `CharacterInputs`). Their tests check requests and readers against pinned
  copies of the contract's schemas and recorded answers in
  `core-model/src/test/resources/character/` (its README says from which
  versions and how they were recorded).
- `core-network` `CharacterSession` owns the rules. Saves go one at a time on
  the newest revision. A save whose answer was lost is sent again unchanged,
  with the same operation ID, and later changes wait behind it. A conflict
  drops a play change; an edit is made once more on the inputs saved
  elsewhere. The app never adopts rules: changed or unavailable rules make the
  sheet read-only and send the player to the website. The sheet loads again
  when the add-on's data changes and when its screen returns.
- The device copy (`SiteStore`, `character-copy.v1`) is the newest answer,
  kept with the role it was received under, shown only to that role and only
  until the host answers. An answer that the sheet is not for this session
  (signed out, forbidden, not found, not offered) ends its showing, and
  forbidden or not found removes it.
- Before changing the sheet, read the Character Sheets add-on's `contracts/`
  (`character.service.json` and the schemas) and `docs/`; Czech names that
  its `locales/cs.json` has are taken from there, so the phone and the website
  agree.

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
- Each site keeps its campaign copy, map details, character sheet copies and
  "My character" on the device (`SiteStore`); removing the site removes them.
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

Models, wire parsing, the host client, the repositories, `EventWriter`, the
character session, the sheet's presentation rules and the session-note
ViewModels have JVM tests. Network and ViewModel tests run against
`FakeHost` (MockWebServer), shared as `core-network` test fixtures; app tests
use Robolectric for Android classes such as navigation routes. For UI or
protocol changes, also run the app on an emulator against a throwaway local
host (README); never point it at a live site's data during development.
