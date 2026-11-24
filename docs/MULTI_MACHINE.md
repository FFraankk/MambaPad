# 多电脑开发与工作方式

## 推荐方案

GitHub 保存源码、通用开发约定和精简交接状态；每台电脑独立克隆、独立工具与缓存；GitHub Actions 统一编译和测试，Windows 真机承担实际鼠标、USB 和蓝牙验证。

不要用网盘实时同步整个仓库、.git、Gradle 缓存或 SDK。切换电脑通过提交和推送同步；不能依赖另一台电脑的未提交文件或聊天记录。

## 在新电脑开始

把工作区放到 touchpad 下，示例：

```text
git clone https://github.com/FFraankk/MambaPad.git
cd MambaPad
git status
```

先读根目录 AGENTS.md，再读 [HANDOFF.md](HANDOFF.md)，最后根据任务查看 BUILDING.md 与相关架构文档。通用约定和交接文件必须实际提交并推送后，另一台电脑才能读到。

每台电脑自行配置 GitHub 登录。本机 SDK/JDK/Go 路径写在未跟踪的本地配置或当前进程环境变量中，不写进共享源码。工具版本以 Gradle Wrapper、Gradle 配置和 go.mod 为依据。

现阶段构建需要 JDK 17+、Android SDK 35、Go 1.25+；CI 固定 JDK 21、SDK 35、Go 1.27.4 等具体版本，避免各电脑随意追最新版本。工具、缓存和输出仍放在 touchpad 工作区；实际初始化脚本尚待实现，当前按 BUILDING.md 配置。

## 切换电脑的日常步骤

**开始工作**

1. 看 git status、当前分支、HANDOFF 和最近提交，确认上台电脑已经推送。
2. 没有本地改动且分支正确时执行 git pull --ff-only；若分支分叉，先看双方提交再处理，不直接 reset 或强推。
3. 新任务从更新后的 main 创建功能分支，例如 feat/bluetooth-reconnect；已经有进行中的分支就继续它。

**结束工作**

1. 记录改了什么、验证到哪一步、哪些没有验证、下一步是什么。
2. 检查差异和待提交文件，提交本次源码与 HANDOFF，再推送对应分支。
3. 即使任务没完成，也可保存到明确的工作分支，说明是进行中状态；不要为了切换电脑把半成品直接混入 main 或发布。

**同一任务换电脑**

在另一台电脑 fetch 后切到同一个功能分支并更新。个人开发优先同一分支同一时间只由一台电脑写入；需要并行时分开分支，最后合并。

Git 的 --ff-only 会在不能快进时拒绝合并，便于发现两台机器各自已有提交的情况，而不是悄悄产生额外合并。[官方说明](https://git-scm.com/docs/git-pull)

## 同步边界

| 内容 | 处理方式 |
| --- | --- |
| 源码、AGENTS、架构、协议、HANDOFF | Git 提交与同步 |
| APK/EXE、发布 ZIP、校验值 | Actions artifacts 或 GitHub Releases，不进源码历史 |
| SDK、JDK、Go、本地缓存、机器路径 | 各机器独立配置，不共享绝对路径 |
| GitHub 凭据、ADB 私钥、日志、签名密钥 | 不进公开仓库；授权和私密备份单独管理 |
| 真机/桌面实测结果 | 把版本、环境和结论同步进文档，不默认别的机器也通过 |

## 构建与签名

- 普通 push/PR：只做构建和单测，不使用发布签名、不自动发布 Release。
- 手动运行 Release 工作流（main）可下载统一签名的验证产物；推送新版本标签才自动打包、核验并发布。
- Actions artifacts 可供不同电脑下载构建结果；面向用户的版本仍放 Releases。[GitHub 官方说明](https://docs.github.com/en/actions/tutorials/store-and-share-data)
- Windows Go 测试需要 Windows runner；Android 编译/JVM 测试可单独运行。USB、蓝牙和真实桌面行为不能仅靠云端构建证明通过。

MambaPad 现有 APK 沿用同一预览 debug 密钥。Actions 已通过仓库 Secrets 注入该密钥，生成 APK 后与公开证书指纹比对，避免 runner 自动生成新密钥导致无法覆盖安装。新电脑可下载 Release 或手动 Release 工作流的产物；若本地覆盖安装，须从私密备份恢复密钥并设置 BUILDING.md 中的签名环境变量。不要卸载用户应用来绕过签名不匹配。

密钥与口令不通过公开 Git 同步，Base64 不是加密。已提供加密备份和恢复脚本，仍须将备份与恢复口令分开离线保存。签名 Secrets、标签发布规则及恢复步骤见 [发布流程](RELEASING.md)。正式签名迁移尚未实施。
