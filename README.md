# TTRPG Companion

An Android app for players and DMs of TTRPG Codex campaign sites.

## What works

- **Sites:** add any number of campaign sites by address and sign in with the
  site's player or DM password, or read the public campaign without one. The
  app reopens the site used last.
- **Overview:** each site opens on its party, the last session's events,
  recently changed records, what you read last and every page with records.
- **Reading:** every record page (characters, locations, events, mysteries,
  factions, pantheon, artifacts, history, companions) with its portrait,
  facts, Markdown article, known facts and questions, relationships, related
  records, the records that mention it and a preview of a location's own map.
  Wiki links open records natively; pages filter by name, party and status;
  search finds every word, in any order and ignoring accents, across names,
  subtitles, tags and excerpts, as the website does. A DM sees one entry per
  public/DM twin pair, as on the website.
- **Maps:** the world map and each location's own map, panned and pinched
  across the site's map tiles (or the original image when the site does not
  tile it), with a marker for every placed location this session may see and,
  on request, the story trail of events. Tapping a marker opens its page; a
  location's page shows it on its map.
- **Session notes:** sessions list the campaign's events by session; start
  the next session, write a note whose first line becomes the event's name,
  or open the full editor for its session, date, priority, summary,
  description and the characters and places involved (DMs can keep an event
  to themselves). Saving needs a connection and a signed-in session. Leaving
  with an unsent note or unsaved changes asks first, leaving while a save is
  on its way offers to wait for it, and a save keeps going if the screen is
  left anyway.
- **Look and language:** the website's fonts (Cinzel and Lora), the site's
  Classic or Moonlit colours in light or dark, and English or Czech following
  the site's language.
- **Offline and failures:** the last campaign copy stays on the phone; without
  a connection the app shows it and says how old it is. Maps and map tiles
  already viewed stay viewable from a bounded cache. A failed load, sign-in or
  save says why in the reader's language (no connection, timeout, an error
  from the site) and offers to try again.
- **Live:** while a site is open, changes made on the website appear within
  seconds; reconnecting catches up on what was missed.
- **Website:** anything without a native screen (the Builder, DM tools, map
  editing, add-ons) opens in an in-app web view that is already signed in.
  A page keeps its unsaved work when the phone rotates, file inputs open the
  file picker for uploads, and a page that cannot load says why with "Try
  again". Downloads from the site (backups, exports) do not work in the app.
- **Updates:** at start the app checks GitHub quietly; when a newer release
  exists, the Settings button (or the menu on a site's home) shows a dot, and
  Settings → Updates downloads, verifies and installs it.

Planned next (host backlog): the optional character sheet module, for sites
running host 2.2.0 or newer with Character Sheets installed.

## Requirements

- A TTRPG Codex 2.x site over HTTPS.
- Android 8.0 or newer.

## Build

Open the folder in Android Studio and run the `app` configuration, or:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug test lintDebug
```

The debug build installs beside a release build as
`eu.junak.ttrpg.companion.debug`.

## Testing against a local host

Run the host on a throwaway data directory (see the host's README), for
example on `127.0.0.1:3077`, then forward that port into the emulator and add
the site as `http://localhost:3077`. Debug builds accept plain HTTP only for
local hosts.

```powershell
adb reverse tcp:3077 tcp:3077
```

## Releases

Every push to `main` that passes CI publishes a signed APK as release
`v0.1.<build>`. Before the first release, create a signing key once and add it
to the repository's Actions secrets:

```powershell
keytool -genkeypair -v -keystore companion-release.jks -alias companion -keyalg RSA -keysize 4096 -validity 36500
```

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `KEYSTORE_STORE_PASSWORD` | The keystore password |
| `KEYSTORE_KEY_ALIAS` | `companion` |
| `KEYSTORE_KEY_PASSWORD` | The key password |

Keep the keystore outside the repository; losing it means players must
uninstall before the next update. Players install the first APK from the
release page (allowing installs from the browser once) and update from the
app afterwards.

## Layout

```text
app/             Compose UI, navigation, theme
core-model/      Host wire formats and campaign presentation rules (pure Kotlin)
core-network/    HTTP client, sessions, sites, campaign cache, live events
feature-update/  GitHub release updates
```
