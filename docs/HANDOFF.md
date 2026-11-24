# 当前开发交接

## 当前状态

- 仓库：https://github.com/FFraankk/MambaPad 。main 与 develop 的功能代码已通过 PR #2 合并，功能提交为 5666d8e79e643359904aa80a8f2681094826d9de。
- 当前发布目标为 1.3.0 正式版（versionCode 10），通过 v1.3.0 标签触发 Actions 构建和发布；最终公开状态以 GitHub Release 为准。旧 v1.3.0-preview 标签及附件保持不变。
- Android 继续使用现有 debug 构建及兼容证书；Windows 未签名。主要支持 USB＋Windows，蓝牙兼容性仍在完善。

## 已完成与验证

- 横屏系统输入法入口、紧凑触控布局及收起恢复；输入法确认后才发送文字。
- 两种模式及横竖屏均提供 Ctrl+C / Ctrl+V 按钮；Windows 使用与 APK 一致的暖色、圆角卡片、橙色按钮，保留原生托盘及设备选择。
- 上述功能源码的 Android 构建、20 项 JVM 测试、Mi 10 真机完整 instrumentation、Windows Go test/vet/build、便携及安装包构建通过；PR 和 main CI 均通过。
- 本轮真机回归使用 dry-run 接收端，快捷键断言验证发送回调；未实测真实电脑剪贴板或蓝牙配对端到端。Windows 已检查设备选择、重连、托盘隐藏和恢复、关闭与退出。
- 安装、覆盖升级、卸载及设置保留测试来自先前 1.3.0-preview 验证；新外观未重新执行实际安装／卸载。真实震动、多手机、多 DPI、Explorer 崩溃恢复未全面验证；完整 Android lint 的既有问题未修复或屏蔽。

## 后续工作边界

- 测试辅助 APK 已移除，保留手机主包；本轮接收端已退出，共享 ADB 未终止。
- 工具、凭据、签名与构建产物不进入 Git。签名离线恢复方式见 RELEASING.md；正式签名迁移与 Windows 代码签名未实施。
- CI 校验构建与单测；Release 工作流校验证书、版本、包完整性、远程摘要后发布五项附件。自动构建不替代设备或安装实测。
- 下一项实施由用户请求确定，不自动实施路线图或咨询中的设想。
