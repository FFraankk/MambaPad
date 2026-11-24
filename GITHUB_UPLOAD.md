# 仓库与发布维护

源码仓库：https://github.com/FFraankk/MambaPad

源码放在仓库根目录。`release/` 已通过 .gitignore 排除，Android / Windows 的 ZIP 与 SHA256SUMS.txt 作为 GitHub Releases 附件发布，不进入源码历史。

当前版本为 v1.3.0-preview，保留预发布标记，不设为 Latest。后续发布时更新版本号、CHANGELOG、发布说明与校验值；保留 LICENSE、NOTICE.txt 以及发布包中的第三方许可。

SDK、开发工具、Gradle 缓存、设备调试授权、签名密钥和测试日志均不应提交。构建步骤见 BUILDING.md。

完整发布步骤见 [发布流程](docs/RELEASING.md)，协作约定见 [AGENTS.md](AGENTS.md)。
