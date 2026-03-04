# QB Remote Android / qB 远程控制（Android）

> Bilingual README（中文 + English）

## 中文说明

### 项目简介
`QB Remote Android` 是一个用于远程控制 qBittorrent WebUI 的 Android 客户端（Compose 实现），支持手机上查看种子、管理连接、执行常用操作。

### 当前功能
- 连接并登录 qBittorrent Web API（HTTP/HTTPS）
- 仪表盘查看全局上传/下载速度、总流量、状态分布
- 种子列表页：
  - 搜索种子（名称/标签/分类/Hash/路径等）
  - 排序（顺序/逆序）：
    - 添加时间
    - 上传速度
    - 下载速度
    - 分享比率
    - 总计上传
    - 总计下载
    - 种子大小
    - 活动时间
    - 做种人数
    - 下载人数
    - 辅种数量
- 种子详情页：
  - 查看详细信息/服务器信息/用户信息/文件信息
  - 暂停、开始、删除
  - 修改名称、路径、分类、标签
  - 调整限速与分享比率
- 添加种子（链接 / `.torrent` 文件）已在种子列表页入口
- 多服务器配置：
  - 主界面右上角 `+` 可新增多个 qB 服务器
  - 支持保存、切换连接、删除服务器配置
- 可靠性增强：
  - 增量同步（`/api/v2/sync/maindata`）
  - 认证重试与会话恢复
  - 凭据加密存储（`EncryptedSharedPreferences`）

### 本地开发
1. 使用 Android Studio 打开项目
2. Gradle Sync
3. 连接真机或启动模拟器
4. 在 App 中填写 qB 服务器地址、端口、用户名、密码后连接

### qBittorrent 端配置
1. `Tools -> Options -> Web UI`
2. 开启 `Web User Interface (Remote control)`
3. 设置端口（默认 `8080`）
4. 设置用户名与密码
5. 确保手机可访问该主机（局域网/防火墙/路由）

---

## English

### Overview
`QB Remote Android` is an Android client (built with Compose) for controlling qBittorrent WebUI remotely.

### Features
- Connect/login to qBittorrent Web API (HTTP/HTTPS)
- Dashboard with global transfer stats and state summary
- Torrent list page:
  - Search torrents (name/tags/category/hash/path)
  - Sorting (ascending/descending):
    - Added time
    - Upload speed
    - Download speed
    - Share ratio
    - Total uploaded
    - Total downloaded
    - Torrent size
    - Last activity
    - Seeders
    - Leechers
    - Cross-seed count
- Torrent detail page:
  - Info/server/user/file tabs
  - Pause/resume/delete
  - Rename, set location/category/tags
  - Speed limit and share-ratio updates
- Add torrent entry moved to torrent list page
- Multi-server management:
  - `+` on dashboard to add multiple qB server profiles
  - Save/switch/delete server profiles
- Reliability:
  - Incremental sync (`/api/v2/sync/maindata`)
  - Session recovery + retry
  - Encrypted credential storage

### Local Development
1. Open the project in Android Studio
2. Run Gradle Sync
3. Launch on emulator/device
4. Enter qB host/port/credentials and connect

### qBittorrent Setup
1. `Tools -> Options -> Web UI`
2. Enable `Web User Interface (Remote control)`
3. Set port (default `8080`)
4. Set username/password
5. Ensure the phone can reach the host (LAN/firewall/router)

## License
Private/internal project unless otherwise specified by repository owner.
