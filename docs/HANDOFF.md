# 当前开发交接

本文件只保留当前有效状态。开始先核对分支、git status、远程提交和本文件；历史细节见 CHANGELOG 与验证记录。

## 已发布版本

- 仓库：https://github.com/FFraankk/MambaPad
- [v1.3.0-preview](https://github.com/FFraankk/MambaPad/releases/tag/v1.3.0-preview) 已于 2026-11-24 发布，保留预发布标记，不设 Latest。
- 发布标签与功能源码提交：6fc6aff633aa40932d23f6c0b754c2d337a8c010。发布后的交接文档更新位于 main，不改动该标签。
- Android versionCode 9，沿用与先前预览版兼容的 debug 签名；Windows EXE 与安装包未签名。
- Release 包含 Android APK、Android ZIP、Windows 便携 ZIP、Windows 安装 EXE、SHA256SUMS.txt，共 5 个附件；已逐项核对远程大小与 SHA256，确认公开状态及标签目标。
- Windows EXE 从上述干净提交构建，嵌入的 vcs.revision 一致且 vcs.modified=false。下载产物留在工作区 release/ 与 dist/，不进入源码 Git 历史。

## 本轮完成

- 清理首页权限宣传、旧主页截图、无关品牌声明与外部项目研究内容，明确历史测试版本边界。
- Android 增加触控板轻点、左右键、拖动开始及共享键盘的系统触觉反馈和总开关，不改变既有鼠标判定时序。
- Windows 增加原生连接窗口、托盘、设备选择、单实例、设置和有限日志；提供便携 ZIP 与按当前用户安装／卸载的安装包及打包脚本。

## 验证与限制

- Android 构建、20 项 JVM 单测、整套应用内真机 instrumentation 通过；系统反馈请求和设置页截图已核验。
- 16 项 Go 单测及 vet 通过；Windows 实测连接、设备选择、托盘隐藏、跨副本单实例，以及安装、运行中覆盖升级、卸载和设置保留。
- 真实震动手感、多手机、蓝牙端到端、Explorer 崩溃恢复及多种 DPI 尚未全面确认；本轮未重新人工测试真实桌面框选。
- 完整 lint 仍有 41 个既有错误、105 个警告；共享页和反馈模块无 lint 错误，未添加屏蔽或 baseline。
- 完整边界见 [1.3 验证记录](desktop-haptics-1.3.md)。

## 环境与下一步

- 手机保留 1.3.0-preview 主包，辅助测试 APK 已移除；电脑测试安装、注册项和快捷方式已清理，接收端已退出，未终止共享 ADB。
- 工具、签名、认证、缓存与产物留在 touchpad 工作区；不提交本机路径、凭据或密钥。已保留构建缓存，不预设其他机器具备相同环境。
- 已实现 CI（push/PR 构建测试）与 Release（手动签名打包验证、版本标签自动发布），Actions 固定提交 SHA，发布时校验 APK 证书、身份及版本，草稿附件摘要核验后才公开；不覆盖既有 Release。
- 已配置四项 Android 签名 Secrets，沿用现有预览证书；密钥加密备份已完成解密比对、实际恢复和篡改拒绝测试。备份及恢复口令当前仍在本机，用户须分开转存离线介质。正式签名迁移和 Windows 代码签名未实施。
- 本地通过 Android 构建/JVM 单测、Windows Go test/vet/双包打包、Actions 静态检查、版本标签不匹配/缺失签名拒绝，以及四附件与校验清单检查。待 GitHub workflow 权限网页授权后推送并执行云端 CI 与手动 Release 验证；尚不能宣称云端已通过。
- 下一项实施由用户请求确定；路线图与咨询不自动成为任务。系统变更和真实设备测试先核对当前会话授权，不重复索取已有授权。
