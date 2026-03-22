# qbitremote / TorrentRemote

`TorrentRemote` is an Android app for remotely managing both qBittorrent and Transmission servers.

`TorrentRemote` 是一个 Android 远程管理工具，支持同时连接和管理 qBittorrent 与 Transmission 服务器。

## Highlights

- Multi-server profiles with fast switching
- Home dashboard with realtime upload/download speed chart
- Server dashboard with independent cached snapshots
- Unified torrent list with search, sorting, and cross-seed hints
- Torrent detail tabs for info, trackers, peers, and files
- Tracker copy, edit, delete, and passkey masking controls
- Reannounce, recheck, per-torrent limits, ratio, category, and tag operations
- Light / dark / custom theme support with Chinese and English localization

## Current Release

- App name: `TorrentRemote`
- Application ID: `com.hjw.qbremote`
- Version: `0.1.10`
- Version code: `11`
- Min SDK: `26`
- Target / Compile SDK: `35`

## Supported Backends

- qBittorrent WebUI API
- Transmission RPC

## Main Features

### 1. Connection and Profiles

- Connect with host/IP or full `http(s)://` URL
- Save multiple server profiles and switch quickly
- Per-profile refresh interval and encrypted credential storage

### 2. Dashboard

- Aggregate home view across multiple servers
- Realtime speed curve for total upload/download traffic
- Per-server dashboard snapshots with chart cards
- Country, category, tag, tracker-site, and state distribution views

### 3. Torrent List

- Unified list with search and multiple sort modes
- Stable return-to-selected-item behavior after leaving detail view
- Double-tap top area to jump back to top

### 4. Torrent Detail

- Tabs: Info / Server / Peers / Files
- Reannounce and recheck actions
- Tracker management with copy, edit, delete, and passkey show/hide
- Unified peer summary card
- File tree browsing with folder-first navigation
- Rename, move, category/tag updates, share ratio, and per-torrent speed limits

### 5. UX and Reliability

- Better server-switch isolation to avoid stale page residue
- Cached state restore when returning from background
- Adaptive launcher icon and Google Play assets
- Chinese / English UI

## Build With Bundled Toolchain

This repository includes a local Android toolchain under `tools/android-build/`.

Debug build:

```powershell
.\gradlew.bat assembleDebug
```

Google Play release AAB with the fixed signing key:

```powershell
.\scripts\build-release-aab.ps1
```

Signed release APK:

```powershell
.\gradlew.bat assembleRelease
```

Key outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

## Google Play Assets

- 512 icon PNG: `play-assets/icon/qbitremote-play-icon-512.png`
- 1024 source PNG: `play-assets/icon/qbitremote-play-icon-1024.png`

## Google Play Docs

- [Google Play Release Checklist (zh-CN)](docs/google-play/PLAY_RELEASE_CHECKLIST.zh-CN.md)

## License

See [LICENSE](LICENSE).
