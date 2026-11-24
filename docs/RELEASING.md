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
- SHA256SUMS.txt：独立 APK、Android ZIP、Windows 便携 ZIP 和安装 EXE 的 SHA256，最终打包后生成。
- RELEASE_NOTES.md：更新、升级方法、兼容性、实际验证和已知限制；作为发布正文。

ZIP 不含临时测试 APK、ADB 私钥、本机路径配置、设备日志或签名密钥。先检查 ZIP 文件列表、CRC 和许可文件。

## 4. 上传与发布

1. 检查暂存差异，提交源码并推送；确认远程提交与本地一致。
2. 使用明确的提交 SHA 创建预发布草稿，而不是依赖可能变动的 main。
3. 上传独立 APK、Android ZIP、Windows 便携 ZIP、Windows 安装 EXE 和 SHA256SUMS.txt，共五项附件。
4. 比对 GitHub 附件 digest/大小与本地文件，检查说明、目标提交和附件数量。
5. 正式发布草稿，预览版本保留 prerelease 标记且不设 Latest；GitHub 不允许预发布同时成为 Latest，只有用户决定正式发行后才使用正式发布策略。
6. 确认公开 Release 不再为 draft，标签指向预期提交，下载附件完整。保留以前版本，不覆盖旧标签或旧附件。

GitHub CLI 如需登录，让用户通过网页完成授权；认证信息只交给 CLI/系统凭据存储，不输出令牌或提交凭据目录。不为推送修改全局 Git 设置，可使用当前任务的凭据辅助配置。

## 5. 完成说明

给出仓库与 Release 链接、版本、重要兼容说明，以及未完成的验证。上传失败时说明具体停在哪一步，不把草稿说成已发布。

## GitHub Actions 自动发布

- `CI`：main push 和 PR 在包含程序、测试或构建配置等非文档变更时检查 Android 构建/JVM 单测、Windows Go test/vet/build；仅修改 Markdown、docs/ 或界面截图时跳过。可在 Actions 中手动运行 CI。不使用签名 Secrets、不发布；Release 的标签／手动触发不受此过滤影响。
- `Release` → Run workflow（main）：使用统一预览签名编译、打包、校验证书及版本，在最终阶段汇总四个发行包与校验清单到 `verified-release` artifact，保留 7 天；不创建或更新 Release。
- 推送新标签 `vVERSION`：标签必须等于 app/build.gradle 的 versionName。全部构建/单测/打包通过后，创建草稿、上传五项附件、比对远程大小与 SHA256，再公开发布。已有同名 Release 时拒绝覆盖；失败草稿保留以便检查，不自动删除或替换。
- 名称含连字符的版本（如 `v1.3.1-preview`）自动预发布且不设 Latest；无预览后缀的正式版本标签会正式发布并设 Latest。因此仅在用户已决定正式发行时推送正式标签。现有 `v1.3.0-preview` 和附件保持原样。

工作流只有三个阶段：

1. metadata：核对版本信息。
2. android / windows：并行构建、测试、签名校验与打包，各上传一次中转产物（保留 1 天）。
3. publish：一次下载并合并中转产物，检查包完整性和生成 SHA256；标签触发时直接发布，手动触发时仅保存 verified-release（保留 7 天）。

不再单独运行 assemble job，也不在标签发布时上传、重新下载完整汇总包。签名备份不属于流水线步骤，不会每次构建重新创建。

工作流固定 Actions 提交 SHA、JDK 21、Go 1.27.4、SDK Platform/Build Tools 35，以及按 SHA256 验证的 ADB 37.0.3、Inno Setup 6.7.4 和解包工具。编译器在临时目录解包，不执行安装器。只有最终 publish job 具有 contents:write；普通 PR 不接触发布密钥。

仓库 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。Base64 只是二进制编码，密钥保护由 GitHub Secrets 承担；密钥临时还原在 runner 临时目录，构建步骤退出即清除，缓存与 artifact 均不包含密钥。APK 必须匹配受信指纹文件。

发布说明自动附带 GitHub 变更摘要与验证边界：APK 沿用现有 debug 证书，Windows 文件未签名；CI 不代表真机触感、USB、托盘或真实安装测试通过，也不掩盖已有 lint 问题。版本发布前仍应更新 CHANGELOG 和适用的真机验证记录。

## 签名的离线备份与恢复

使用 Python 与 cryptography，在仓库外的私密目录执行以下命令；所有目标必须不存在，口令不写命令行或聊天。

```text
python scripts/signing-backup.py create <加密备份.enc> --keystore <原密钥> --recovery-file <恢复口令文件>
python scripts/signing-backup.py verify <加密备份.enc> --keystore <原密钥> --recovery-file <恢复口令文件>
python scripts/signing-backup.py restore <加密备份.enc> --keystore <新密钥路径> --credentials-output <私密凭据.json> --recovery-file <恢复口令文件>
```

创建时可交互输入别名和密钥口令，或通过当前进程的 MAMBAPAD_KEY_ALIAS / MAMBAPAD_STORE_PASSWORD / MAMBAPAD_KEY_PASSWORD 注入。备份采用 scrypt 与 AES-256-GCM，包含密钥及其别名、口令；恢复口令单独生成。将加密备份与恢复口令分别转存到独立的离线介质或密码管理器；同盘两个目录不构成灾难恢复备份。恢复产生的凭据 JSON 属于秘密，使用后妥善处理，不加入 Git。GitHub Secrets 无法代替离线备份。

备份保持最小集合：日常构建使用原密钥，灾难恢复只需一份加密备份和其恢复口令。构建缓存、临时验证包、Actions 中转产物与发布附件均不是签名备份，可以独立清理。恢复前后应核对可信证书指纹；新电脑普通调试构建不需要恢复备份，步骤见 [BUILDING.md](../BUILDING.md)。
