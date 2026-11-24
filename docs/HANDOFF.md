# 当前开发交接

最后核对：2026-11-25（Asia/Hong_Kong）。本文件保留接续开发所需的事实；历史变更见 CHANGELOG，待验证项见 ROADMAP。

## 当前状态

- 仓库：https://github.com/FFraankk/MambaPad 。main 与 develop 的功能代码已通过 PR #2 合并，功能提交为 5666d8e79e643359904aa80a8f2681094826d9de。
- [v1.3.0](https://github.com/FFraankk/MambaPad/releases/tag/v1.3.0) 已于 2026-11-25 正式公开并设为 Latest，versionCode 10。标签指向 8cb9999d89deb710e5ef843d616af540d3fd9d22；旧 v1.3.0-preview 标签及附件保持不变。
- 五项公开附件已下载，逐项核对远程摘要、SHA256SUMS、ZIP CRC、许可、APK 身份及可信证书；Windows EXE 的 vcs.revision 等于发布标签提交，vcs.modified=false。
- Android 继续使用现有 debug 构建及兼容证书；Windows 未签名。主要支持 USB＋Windows，蓝牙兼容性仍在完善。
- 发布查询修复提交 0b2749906732e4ee3f104d9029039ac1d6d8274b 已同步至 main / develop，其 [CI](https://github.com/FFraankk/MambaPad/actions/runs/45463693330) 通过。发布标签仍指向上述 8cb9999，不因后续文档／脚本修复移动。

## 已完成与验证

- 横屏系统输入法入口、紧凑触控布局及收起恢复；输入法确认后才发送文字。
- 两种模式及横竖屏均提供 Ctrl+C / Ctrl+V 按钮；Windows 使用与 APK 一致的暖色、圆角卡片、橙色按钮，保留原生托盘及设备选择。
- 上述功能源码的 Android 构建、20 项 JVM 测试、Mi 10 真机完整 instrumentation、Windows Go test/vet/build、便携及安装包构建通过；PR 和 main CI 均通过。
- 本轮真机回归使用 dry-run 接收端，快捷键断言验证发送回调；未实测真实电脑剪贴板或蓝牙配对端到端。Windows 已检查设备选择、重连、托盘隐藏和恢复、关闭与退出。
- 安装、覆盖升级、卸载及设置保留测试来自先前 1.3.0-preview 验证；新外观未重新执行实际安装／卸载。真实震动、多手机、多 DPI、Explorer 崩溃恢复未全面验证；完整 Android lint 的既有问题未修复或屏蔽。

- 普通 CI 已增加文档路径过滤：仅修改 Markdown、docs/ 或界面截图不编译；代码、测试或构建配置变更仍检查，可手动运行。Release 触发方式不变。

## 后续工作边界

- 测试辅助 APK 已移除。手机当前主包仍为 versionCode 9 / 1.3.0-preview（包含本轮功能的测试构建），未安装公开的 versionCode 10 / 1.3.0 附件；不要把功能回归写成正式附件已做真机回归。
- 本轮临时接收端已退出；收尾时发现现有安装版接收端在运行，予以保留。本轮未终止共享 ADB、修改系统设置或清除用户数据。
- 工具、凭据、签名与构建产物不进入 Git。签名离线恢复方式见 RELEASING.md；正式签名迁移与 Windows 代码签名未实施。
- 1.3.0 的 [Release 构建](https://github.com/FFraankk/MambaPad/actions/runs/45462563480) 中 Android、Windows、包汇总均通过；最终草稿查询因按标签接口返回 404 而停止。维护者核验既有草稿的五项附件后完成公开发布，没有覆盖标签或重新上传附件。
- main 已修复草稿查询：分页列出可访问 Release，包含草稿；公开后用 ID 核验。4 项回归检查、真实草稿查询及 Actions 静态检查通过；后续发布在 metadata 阶段运行回归检查。自动构建不替代设备或安装实测。
- 修复后的发布查询有单元测试和真实 API 验证，但尚未用修复后的脚本完成一次新的完整标签自动发布；原失败运行保留，如实记录，不为消除红色状态移动标签或重发版本。
- 下一项实施由用户请求确定；可选验证包括正式附件的覆盖升级、新电脑实际构建、蓝牙端到端及多 DPI。本次未执行这些验证，不自动扩展收尾任务。
- 本次收尾仅更新交接、路线图及协作说明；不创建新 Release。文档提交后同步 main / develop，并保持工作区干净。
