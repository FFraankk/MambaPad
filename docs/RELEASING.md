# 发布流程

发布仅在用户已授权的任务中执行。同一发布任务不重复要求批准；代码修复和文档任务本身不自动代表要发布。

## 1. 确定版本和范围

- 查看远程 main、现有标签和工作区改动，保留别人的工作；不 force push。
- 在 app/build.gradle 更新 versionName 和递增 versionCode；发布标签与 versionName 对应，例如 v1.2.2-preview。
- 更新 CHANGELOG、README 和验证记录，明确实际通过的测试及未验证部分。
- Android 与 Windows 可独立变动。若 EXE 未改，说明旧接收端仍可使用，不要求用户无意义地更新。

## 2. 构建与核验

执行 BUILDING.md / CONTRIBUTING.md 中与本次修改相关的检查；确保 APK 确实来自本次源码。用 aapt dump badging 核对包名、版本号和 versionCode；确认覆盖升级使用兼容签名。

- APK、EXE、签名密钥、SDK 和缓存不进入源码提交。
- 预览 APK 仍为 debug 签名时必须说明；正式签名配置另行管理，不把密钥或认证令牌写进脚本/日志。
- 不能把 JVM 通过写成真机通过，也不能把旧版测试写成本版已重新通过。

## 3. 准备附件

在工作区的 dist/ 或 release/ 准备独立版本目录：

- MambaPad-Android-VERSION.zip：APK、LICENSE、NOTICE.txt、使用说明。
- MambaPad-Windows-x64-VERSION-Portable.zip：EXE、portable.flag、adb-path.txt、说明、LICENSE、NOTICE.txt、Go LICENSE、版本子目录中的官方 ADB 工具及其原始 NOTICE/source.properties。
- MambaPad-Windows-x64-VERSION-Setup.exe：按当前用户安装／卸载，同一接收端核心，不包含便携标记与运行数据。
- SHA256SUMS.txt：Android ZIP、Windows 便携 ZIP 和安装 EXE 的 SHA256，最终打包后生成。
- RELEASE_NOTES.md：更新、升级方法、兼容性、实际验证和已知限制；作为发布正文。

ZIP 不含临时测试 APK、ADB 私钥、本机路径配置、设备日志或签名密钥。先检查 ZIP 文件列表、CRC 和许可文件。

## 4. 上传与发布

1. 检查暂存差异，提交源码并推送；确认远程提交与本地一致。
2. 使用明确的提交 SHA 创建预发布草稿，而不是依赖可能变动的 main。
3. 上传 Android ZIP、Windows 便携 ZIP、Windows 安装 EXE 和 SHA256SUMS.txt。
4. 比对 GitHub 附件 digest/大小与本地文件，检查说明、目标提交和附件数量。
5. 正式发布草稿，预览版本保留 prerelease 标记且不设 Latest；GitHub 不允许预发布同时成为 Latest，只有用户决定正式发行后才使用正式发布策略。
6. 确认公开 Release 不再为 draft，标签指向预期提交，下载附件完整。保留以前版本，不覆盖旧标签或旧附件。

GitHub CLI 如需登录，让用户通过网页完成授权；认证信息只交给 CLI/系统凭据存储，不输出令牌或提交凭据目录。不为推送修改全局 Git 设置，可使用当前任务的凭据辅助配置。

## 5. 完成说明

给出仓库与 Release 链接、版本、重要兼容说明，以及未完成的验证。上传失败时说明具体停在哪一步，不把草稿说成已发布。
