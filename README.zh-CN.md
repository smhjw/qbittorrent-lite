# TorrentRemote

<p align="center">
  <strong>简体中文</strong> | <a href="README.md">English</a>
</p>

![截图](Screenshot_20260315_191729_TorrentRemote.png)

TorrentRemote 是一款用于远程管理 qBittorrent 与 Transmission 的 Android 客户端。

## 亮点功能

- 支持通过主机/IP 或完整 `http(s)://` URL 连接
- 多服务器保存与切换（qBittorrent + Transmission）
- 首页服务器卡片堆栈，快速进入服务器详情
- 仪表盘展示速度、总计流量与状态统计
- 种子列表搜索与排序，详情页操作完整
- 支持磁力/URL/`.torrent` 添加
- 深浅色主题与中英文界面

## 项目信息

- 应用名：`TorrentRemote`
- 应用 ID：`com.hjw.qbremote`
- 版本：`0.1.8`（`versionCode = 8`）
- 最低 SDK：`26`
- 目标/编译 SDK：`35`

## 下载

- 最新 APK：`torrentremote 0.1.8.apk`
- 发布页：https://github.com/smhjw/qbitremote/releases/tag/v0.1.8

## 构建（使用 `tools/` 本地工具链）

本项目使用以下内置工具链构建：

- `tools/android-build/tools/jdk17`
- `tools/android-build/tools/android-sdk`

PowerShell 示例：

```powershell
$env:JAVA_HOME="D:\hjw\codex\qb-remote-android\tools\android-build\tools\jdk17"
$env:ANDROID_HOME="D:\hjw\codex\qb-remote-android\tools\android-build\tools\android-sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:PATH="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat assembleDebug
```

APK 输出：

- `app/build/outputs/apk/debug/app-debug.apk`

## Google Play 文档

- [Google Play 上架清单](docs/google-play/PLAY_RELEASE_CHECKLIST.zh-CN.md)
- [Data Safety 填写建议](docs/google-play/DATA_SAFETY_GUIDE.zh-CN.md)
- [隐私政策（英文）](docs/google-play/PRIVACY_POLICY.md)
- [隐私政策（中文）](docs/google-play/PRIVACY_POLICY.zh-CN.md)
