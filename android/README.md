# 观影中心 (MovieHub) Android

基于 Jetpack Compose 与 Material 3 打造的极简暗黑风 Android 综合影音应用，移植并升级自 `moviehub`。

## 核心功能

- **影视点播 (VOD)**：
  - 豆瓣热门电影 / 剧集 / 综艺 / 动漫海报流，支持按标签快速切换
  - 10 大资源站并发搜索与智能匹配换源
  - **影院级播放器**：支持左侧上下滑调节亮度、右侧上下滑调节音量、双击左右半屏快退/快进 10 秒、长按 2.0x 倍速、一键跳过片头 (+90s)、分集区间折叠以及「我的片单」本地收藏
  - 已彻底关闭系统画中画 (PiP) 及网页端小窗干扰
- **分类片库**：多源分类树浏览，支持按类型、年份、最新/最热/评分筛选
- **电视直播**：内置央视、卫视、地方台与国际频道，支持自定义 M3U8 直播源添加
- **Spotify 风格音乐中心**：
  - 酷狗 / 网易云 / 酷我 / QQ 音乐 / 咪咕五大平台多通道搜索
  - 智能跨平台音源解析兜底与本地流媒体反代支持
  - 专辑黑胶封面与全屏同步歌词切换、喜欢的音乐红心歌单

## 获取 APK 安装包

1. **在 AI Studio 中一键推送到 GitHub 自动打包**：
   - 点击 AI Studio 右上角菜单中的 **Push to GitHub** 将本项目推送到你的 GitHub 仓库。
   - 推送完成后，仓库内置的 GitHub Actions (`.github/workflows/build-apk.yml`) 会自动运行，编译生成 `MovieHub-v1.0-debug.apk` 并自动发布到你仓库右侧的 **Releases** 页面。
2. **在 AI Studio 中直接下载 APK**：
   - 点击 AI Studio 右上角菜单（设置 / 导出图标），选择 **Download APK / Generate APK** 即可直接下载安装包。

## 仓库结构说明

- `android/`：Android 客户端（Jetpack Compose），CI 以 `./android` 为工作目录构建
- `sources.json`：仓库根级远程源配置，客户端启动时自动拉取（GitHub Raw / jsDelivr / 镜像）
- `version.json`：在线检查更新清单，客户端读取 `versionName` / `versionCode` / `apkUrl`
- `source-hub/`：零依赖 Node 源站运维后台，同时通过 GitHub Pages 部署为在线源站
- `backend-java/`：Spring Boot + MySQL 源站管理服务（可选自建）
- `moviehub/`：Electron 桌面版

修改 Android 代码后推送 `main`，`.github/workflows/build-apk.yml` 会自动构建并发布 Release APK。
