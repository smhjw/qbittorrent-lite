# QB Remote (Android MVP)

Android remote control app for qBittorrent WebUI.

## Features

- Connect and login via qBittorrent Web API
- Global transfer panel (download/upload speed and totals)
- Torrent list with progress, rates, and status labels
- Torrent actions: `Pause`, `Resume`, `Delete` (with or without files)
- Status filters: `All`, `Downloading`, `Seeding`, `Paused`, `Completed`, `Error`
- Sorting options: `Activity time`, `Added time`, `Upload speed`, `Download speed` (ASC/DESC)
- Settings panel:
  - show/hide speed totals
  - enable/disable server grouping
  - show/hide chart panel
  - show/hide site names in chart
  - chart sort mode selector
  - delete files default behavior toggles
- Auto refresh with configurable interval

## Reliability and Security Improvements

- Incremental sync via `GET /api/v2/sync/maindata?rid=...`
  - Keeps a local torrent cache and applies deltas from qB
  - Falls back to full endpoints for older servers that do not support sync API
- Session recovery
  - Auto re-login on `401/403` and retry request
  - Exponential backoff retry for network failures and `5xx`
- Credential storage hardening
  - Password moved from plain DataStore to `EncryptedSharedPreferences`
  - Automatic one-time migration from legacy DataStore password key

## qBittorrent Setup

In desktop qBittorrent:

1. Open `Tools -> Options -> Web UI`
2. Enable `Web User Interface (Remote control)`
3. Set WebUI port (default `8080`)
4. Set username and password
5. Ensure firewall/router allow access from your phone if needed

## Run

1. Open `qb-remote-android` in Android Studio
2. Run Gradle Sync
3. Start on device/emulator
4. Fill in app connection form:
   - Host/IP (for example `192.168.1.12`)
   - Port (for example `8080`)
   - Username/password
   - HTTPS toggle
5. Click `Connect`

## Current MVP Gaps

- No certificate pinning or self-signed cert trust flow yet
- No batch actions yet
- No multi-server profile management yet
- No CI/test pipeline yet

## Suggested Next Steps

1. Batch actions (multi-select pause/resume/delete)
2. Multi-server profiles (save/switch quickly)
3. Search + sort + grouping stats
4. Unit tests with `MockWebServer` + ViewModel tests
