# BedrockSync

A **fully client-side** Fabric mod for Minecraft Java Edition 26.2. It signs in with a Microsoft account through the official device code flow, shows the Xbox gamertag, and provides a local skin file check.

> **Status: unverified build.** This archive was generated without the ability to compile it. Do not treat it as working until the GitHub Actions build passes and the jar has been tested in game. See "Build" below.

## What it does

- Microsoft sign-in via OAuth 2.0 device code flow (you type your password only on microsoft.com).
- Xbox Live and XSTS authorization, then display of your **Xbox gamertag** and XUID.
- Account screen with sign in, sign out, and a local skin check.
- Local skin check reads `config/bedrocksync/skin.png` and validates its size (64x64 or 64x32).
- A **BedrockSync** button on the title screen.

## What it does not do

- **It does not let a Bedrock-only account join Java online-mode servers.** Java multiplayer requires a Java Edition profile, which this sign-in flow does not provide. Owning Bedrock does not grant Java ownership, and this mod does not bypass that check or forge sessions.
- It does not download or apply Bedrock skins, and it does not upload skins to Mojang or Microsoft.
- It does not store Microsoft tokens. Tokens exist only in memory during sign-in. Only the gamertag and XUID are kept for display, and they are cleared on sign-out or game exit.
- It has no server-side component. Server integration (Geyser, Floodgate, XBridge Auth) is out of scope and optional.

## Requirements

| Component | Version |
|---|---|
| Minecraft Java Edition | 26.2 |
| Java runtime | 25 |
| Fabric Loader | 0.19.1 or newer |
| Fabric API | 0.150.1+26.2 |
| Launcher | MJ Launcher (Android) or any Fabric-capable launcher |

## One-time setup: register an Entra app

Microsoft requires a client ID for the device code flow. You create your own; BedrockSync does not ship one.

1. Go to the Microsoft Entra admin center (entra.microsoft.com) and sign in with your Microsoft account.
2. Open **Identity > Applications > App registrations > New registration**.
3. Name: `BedrockSync`. Supported account types: **Personal Microsoft accounts only**. Leave Redirect URI empty. Click **Register**.
4. Copy the **Application (client) ID**.
5. Open **Authentication**, then **Advanced settings**, and set **Allow public client flows** to **Yes**. Save.
6. Create the config file. After the first launch it is created at `.minecraft/config/bedrocksync.json`. Set it to:

```json
{
  "client_id": "PASTE-YOUR-APPLICATION-CLIENT-ID-HERE"
}
```

## Install with MJ Launcher (Android)

1. In MJ Launcher, create or select a **Fabric 26.2** instance. Use Java 25.
2. Download `bedrocksync-1.0.0.jar` from the GitHub Actions artifacts (or a release).
3. Download **Fabric API 0.150.1+26.2** and place it in the instance's `mods` folder.
4. Place `bedrocksync-1.0.0.jar` in the same `mods` folder.
5. Launch the game once so the config file is created, then set `client_id` as described above.
6. Optional skin check: place a 64x64 PNG at `config/bedrocksync/skin.png`.

## Using the mod

1. On the title screen, tap **BedrockSync** (top right).
2. Tap **Sign in with Microsoft**. A URL and a code appear.
3. On any browser, open the URL, enter the code, and approve the request with your Microsoft account.
4. The screen shows your gamertag once sign-in finishes.

## Build

The project builds with Gradle, using Java 25 and Fabric Loom 1.15.2.

**On GitHub:** push this repository. The workflow in `.github/workflows/build.yml` runs automatically on every push. Download the `bedrocksync-jar` artifact from the run page.

**Locally:** run `gradle wrapper --gradle-version 9.3.1` once to generate the wrapper JAR, then `./gradlew build`. The jar appears in `build/libs/`.

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| "Set client_id in config/bedrocksync.json" | The `client_id` field is empty. Complete the one-time setup. |
| "Could not start Microsoft sign-in (HTTP 400/401)" | The client ID is wrong, or **Allow public client flows** is not enabled. |
| "no Xbox profile" | Create a gamertag on xbox.com with the same Microsoft account. |
| "Xbox Live is not available in your country or region" | Xbox services are not offered in your region for this account. |
| Skin says "Image decoding is unavailable" | The Java runtime lacks `java.desktop` (ImageIO). Use a full OpenJDK 25 runtime. |
| Game fails to start with a Fabric error | Check that Loader is 0.19.1+ and Fabric API matches 26.2. See `logs/latest.log`. |
| Title screen button missing | Another mod may be changing the title screen. Check the log for BedrockSync errors. |

Logs are written to `logs/latest.log` and tagged `bedrocksync`. Tokens are never logged.

## Project layout

```
src/main/java/dev/bedrocksync/
  BedrockSyncClient.java          client entrypoint and title screen button
  auth/MicrosoftAuthService.java  device code flow, Xbox Live, XSTS
  auth/BedrockSession.java        in-memory sign-in state
  config/BedrockSyncConfig.java   config/bedrocksync.json (non-secret settings only)
  skin/LocalSkinManager.java      local skin file check
  gui/BedrockSyncScreen.java      account screen
src/main/resources/fabric.mod.json
.github/workflows/build.yml       CI build with JDK 25
```

## Server integration (optional, not implemented)

Joining Java servers with a Bedrock identity requires server-side software such as Geyser and Floodgate, or an authentication bridge such as XBridge Auth. Those are server installs. BedrockSync does not depend on them.

## License

MIT. See `LICENSE`.
