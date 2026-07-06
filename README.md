# High Noon

A **two-phone western duel game**: two Android devices find each other over **Wi-Fi Direct**, challenge each other, and face off in a reflex contest. After a random wait the signal fires — whoever draws (jerks the phone upward) fastest wins; whoever moves *before* the signal false-starts and loses. The winner can take a selfie and a photo and send them to the loser as a trophy.

Project for a mobile application development course (Kotlin + Jetpack Compose).

---

## Features

- **Peer-to-peer matchmaking** with no server and no internet connection, via Wi-Fi Direct.
- **Application-level challenge handshake** (accept/decline) with a nickname, independent of the system pairing popup.
- **Reflex duel** driven by the accelerometer (detects the "draw" gesture).
- **Distributed referee**: one device (the Group Owner) acts as judge and decides the verdict without synchronizing clocks.
- **Audio**: western intro music, victory/defeat jingles, sound effects (gunshot, "cheater"), and vibration on the signal.
- **Winner photos** (front-camera selfie + back-camera photo) captured in-app with CameraX, sent to the opponent over the socket, and saveable to the gallery.
- **Themed UI** with a background for each game phase.

---

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (`AndroidViewModel` + observable state) |
| Concurrency | Kotlin Coroutines (`Dispatchers.IO` / `Default`) |
| Networking | Wi-Fi Direct (`WifiP2pManager`) + TCP sockets |
| Camera | CameraX |
| Photo storage | MediaStore (gallery) + FileProvider |

**Versions**

| | |
|---|---|
| `minSdk` | **33** (Android 13) |
| `compileSdk` / `targetSdk` | `<check in app/build.gradle>` |
| Android Gradle Plugin | `<check in build.gradle>` |
| Kotlin | `<check in build.gradle>` |
| Compose BOM | `<check in app/build.gradle>` |
| CameraX | `1.4.x` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| JDK | 17 (bundled with Android Studio) |

> The `minSdk 33` floor is a deliberate choice: it allows the use of modern APIs (the `NEARBY_WIFI_DEVICES` permission, `VibrationAttributes`, the `getParcelableExtra(Class)` overload, etc.) without backward-compatibility branches.

---

## Requirements

- **Android Studio** (recent version, with the bundled JDK 17).
- **Two physical Android devices** running **API >= 33**, both with **Wi-Fi on**.
- **The emulator is not suitable**: Wi-Fi Direct requires real radio hardware, so two real phones are needed.

---

## Project structure

```
it.diunipi.sam.highnoon/
├── MainActivity.kt          # Entry point: single Activity + Compose host (Scaffold, GameScreen)
├── Config.kt                # Centralized constants (Network / Duel / Photo)
├── ui/
│   ├── GameScreen.kt        # "Thin" UI: lobby, challenge dialog, duel screen, background selection
│   ├── CameraCapture.kt     # In-app CameraX camera (preview + capture), reusable
│   ├── DuelText.kt          # Text with a drop shadow, readable on any background
│   ├── ScreenBackground.kt  # Full-screen background image + scrim
│   └── theme/               # Theme.kt, Color.kt (Material 3)
├── game/
│   ├── DuelPhase.kt         # State enums: DuelPhase, Outcome, ChallengeState
│   ├── DuelProtocol.kt      # Text "language" exchanged over the socket
│   ├── DuelViewModel.kt     # Core: state + logic; owns every resource (AndroidViewModel)
│   ├── PhotoCodec.kt        # Photo compression (downscale + JPEG) <-> base64 <-> Bitmap
│   └── GallerySaver.kt      # Saves to the gallery via MediaStore
├── audio/
│   ├── MusicPlayer.kt       # Owns one MediaPlayer for a given track (western / victory / defeat)
│   └── SoundEffects.kt      # Owns the SoundPool (gunshot, cheater)
└── network/
    ├── WifiDirectConnection.kt  # Wi-Fi Direct: WifiP2pManager + BroadcastReceiver (discovery + group)
    └── SocketConnection.kt      # Text-based TCP socket; all I/O on Dispatchers.IO
```

Resources: `res/raw/` (audio), `res/drawable/` (backgrounds), `res/values/strings.xml` (UI text), `res/xml/file_paths.xml` (FileProvider).

---

## Architecture overview

The app follows **MVVM**: the `DuelViewModel` owns the state and logic, while the Compose UI only reads state and forwards events (**state flows down, events flow up**). The classic Android components (sensor, audio, Wi-Fi Direct, socket) live behind dedicated expert classes, each the **sole owner of its own resource**, communicating with the ViewModel through **callbacks that update observable state** (Compose recomposes accordingly).

The two phones communicate as follows: once the Wi-Fi Direct group is formed, the **Group Owner** opens a `ServerSocket` and the other device (the client) connects to its IP; text messages defined in `DuelProtocol` travel over the socket. All blocking operations (accept, connect, read, write) and image encoding run **off the UI thread**, on coroutines.

---

## Build and run

1. **Clone the repository**
   ```bash
   git clone <repo-URL>
   ```
2. **Open the project in Android Studio** and wait for the **Gradle sync** (it downloads the dependencies, including CameraX).
3. **Connect the first phone** (with USB debugging enabled) and press **Run**. Then repeat on the **second phone** — both need the app installed.
4. On first launch the app requests runtime permissions (nearby devices for discovery, camera when taking photos): grant them.

> Tip: make sure **Wi-Fi is on** on both devices (you do not need to be connected to a network).

---

## How to play

1. On **both** phones: enter a **nickname** and press **Find an opponent**. Each one lists the other (it is enough that one sees the other).
2. On **one** of them, tap the opponent in the list to send a challenge.
3. On the **other** phone the dialog **"X wants to duel you"** appears — press **Accept** (or **Decline** to go back to searching).
4. One of the two is the **host** (shown on screen): the host presses **Start duel**. Both enter "Hold still…".
5. After the music and a random wait, the phones **vibrate**: **draw** (jerk the phone upward). Moving *before* the signal is a **false start**.
6. The outcome appears (**YOU WIN / YOU LOSE / DRAW**). The winner can take a **selfie + photo** (or press **Skip**); the loser receives them and can save them to the gallery.
7. The host can press **Play again** for a new round, or **Leave** to return to the lobby.

**If the connection misbehaves** (the two do not find each other, or it gets stuck on "Connecting…"): turn Wi-Fi off and on again on both devices (Wi-Fi Direct groups are persistent, and a group left over from a previous session can interfere with a new one); restart the phones if needed.

---

## Permissions

| Permission | Use |
|---|---|
| `NEARBY_WIFI_DEVICES` (`neverForLocation`) | Wi-Fi Direct device discovery (runtime, API 33+) |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | P2P connection state and management |
| `INTERNET` | Opening TCP sockets on the local group |
| `CAMERA` | Capturing the winner's photos (CameraX, runtime) |
| `VIBRATE` | Vibration on the duel signal |

> Saving to the gallery uses **MediaStore** and, on Android 10+ (`minSdk 33`), requires **no** storage permission.

---

## Required assets

If you clone the project, make sure the following are present:

- **Audio** in `res/raw/`: `western_start`, `victory`, `defeat`, `gunshot`, `cheater`.
- **Backgrounds** in `res/drawable/`: `bg_lobby`, `bg_idle`, `bg_waiting`, `bg_draw`, `bg_win`, `bg_lose`.
- **App icon** (generated with Image Asset Studio into the `mipmap-*` folders).

---

## Known limitations

- **Two players only**: the protocol and referee assume exactly two devices.
- **The host is chosen by the system**: the Group Owner is elected by the two devices' firmware (Group Owner Intent), not by the app; on a given pair it is therefore almost always the same one. A stable host is convenient for a game with a referee anyway.
- **Vibrations are not perfectly synchronized**: the two buzzes can land a few milliseconds apart due to network jitter. This is **purely cosmetic**: each phone measures its own reaction time against its *own* signal, so the latency does not affect fairness.
- **Wi-Fi Direct fragility**: persistent groups and time-limited discovery may require a Wi-Fi reset (see above).
- **Unencrypted channel**: socket communication is in plaintext — fine for a local game, not for sensitive data.
- **Camera**: with CameraX the front camera is guaranteed for the selfie, but rotation/mirror handling depends on the device.

---

## Notes

Developed for educational purposes. Add any license and credits here.