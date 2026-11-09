# TTRPG Companion

An Android app for players and DMs of TTRPG Codex campaign sites.

## What works

- **Sites:** add any number of campaign sites by address and sign in with the
  site's player or DM password, or read the public campaign without one. The
  app reopens the site used last.
- **Reading:** every record page (characters, locations, events, mysteries,
  factions, pantheon, artifacts, history, companions), articles with Markdown
  and portraits, wiki links between records, and search by name that ignores
  accents.
- **Offline:** the last campaign copy stays on the phone; without a connection
  the app shows it and says how old it is.
- **Live:** while a site is open, changes made on the website appear within
  seconds; reconnecting catches up on what was missed.
- **Website:** anything without a native screen (the Builder, DM tools, maps,
  add-ons) opens in an in-app web view that is already signed in.
- **Updates:** Settings → Updates installs newer releases from GitHub.

Planned next (host backlog): the optional character sheet module, which needs
the host's client service access first, and a native map viewer.

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
