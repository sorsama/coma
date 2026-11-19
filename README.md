# Coma

An Android remote for [Hermes Agent](https://hermes-agent.nousresearch.com). Your threads, memory and
keys stay on the machine that runs Hermes; the phone is a window onto it.

<p>
  <img src="docs/connect.png" width="240" alt="Pairing with a host found on the network">
  <img src="docs/approval.png" width="240" alt="Approving a command Hermes wants to run">
  <img src="docs/automate.png" width="240" alt="Scheduled jobs">
</p>

## What it does

- **Threads**: the host's sessions, with replies and tool calls streaming in as Hermes works.
- **Approvals**: when Hermes wants to run something risky, the thread asks. Slide to approve, or deny.
- **Automate**: the host's scheduled jobs, with a switch on each. Describe a new one in plain words
  and Hermes sets it up.
- **Mind**: the skills and toolsets the host has.
- **System**: the host's readiness checks, model and connected messaging platforms.

It talks to Hermes' own [API server](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server),
so there is no Coma backend and nothing is stored in between.

## Pair it with your Hermes

Install the companion plugin on the machine that runs Hermes:

```
hermes plugins install sorsama/coma-hermes --enable --force
```

Hermes' security scan blocks plugins that open a tunnel unless you pass `--force`; the plugin's
README explains what it flags and why. Restart Hermes, then run `hermes coma` in a terminal, or open **Coma** in Hermes Desktop, and scan
the QR with the app. The plugin opens a free Cloudflare tunnel, so the phone reaches the host from
anywhere. Details, including what to know about security, are in
[coma-hermes](https://github.com/sorsama/coma-hermes).

You can also skip the plugin and type an address and API key by hand, for a host on your own
network, VPN or HTTPS proxy.

## Plans

One host is free. **Plus** (99 baht a month, a Google Play subscription with product id
`coma_plus_monthly`) lifts the limit. The limit is counted on the phone; there is no account.

## Build

Android Studio's JDK 17 and the Android SDK (compile SDK 35) are all it needs:

```
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Kotlin and Jetpack Compose, with no UI library
beyond Compose foundation; the design kit is in `app/src/main/java/com/zyphite/coma/ui`.

## Where things are

```
app/src/main/java/com/zyphite/coma/
  data/Hermes.kt      the Hermes API client
  data/Discovery.kt   finding hosts on the local network
  data/Vault.kt       API keys, encrypted with the Android Keystore
  ComaViewModel.kt    app state
  screens/            one file per screen
  ui/                 colours, type, motion, icons
```

## Credits

- Icons: [Boxicons](https://boxicons.com) (MIT)
- Type: [Geist](https://github.com/vercel/geist-font) (SIL Open Font License, see `GEIST-OFL.txt`)
- QR scanning: [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded)

Coma is not affiliated with Nous Research. MIT licensed.
