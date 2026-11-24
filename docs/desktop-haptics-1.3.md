# 1.3.0-preview 验证记录

2026-11-24；Android versionCode 9，沿用现有 debug 签名。本次按用户选择保留预发布标记，不设为 Latest。

## Android

- 小米 10 / Android 11，覆盖安装前核对新旧 APK 签名 SHA256 一致，使用 install -r 保留主包数据。
- assembleDebug、assembleDebugAndroidTest 与 20 项 testDebugUnitTest 通过。
- UsbSmokeInstrumentation 整套通过：普通移动／停留、轻点后拖动、双击、取消释放、双区域滚轮方向、多指重排、四种控制布局、USB／蓝牙返回、输入法 composing／commit／删除与生命周期重连。
- 新增手势反馈断言：首次轻点立即反馈；双击两次反馈；拖动仅在成立时增加一次反馈；取消、普通移动与滚动不额外反馈。鼠标事件仍使用原有分类时序。
- feedback_preview 通过：在主包可见控件上请求系统触觉反馈，并截图检查“触觉反馈”开关和设置页排版；系统振动服务记录了本次预设效果请求。实际手感与强弱仍需用户体验确认。
- 手机限制 adb shell input 注入，未更改安全开关；应用内 instrumentation 可正常分发自己的事件。辅助测试 APK 已卸载，保留 1.3.0 主包。
- lintDebug 报告 41 个错误、105 个警告；错误均位于原有蓝牙／旧页面／旧 sender，修改的共享页和反馈模块没有 lint 错误。未添加 suppress 或 baseline。

## Windows

- Windows 11 10.0.26100.4652，Go 1.27.4 windows/amd64；标准库实现原生窗口／托盘，16 项 Go 单测与 go vet ./... 通过。
- 便携包实测连接手机、显示当前设备、最小化隐藏、另一路径 EXE 唤回已有窗口，始终只有一个接收进程。
- 安装版实测设备列表选择、会话重连、设置保存、当前设备及中文状态显示；实际只有一台 USB 手机，多手机硬件组合尚未实测，设备筛选与身份保留有单测。
- Inno Setup 6.7.4 生成安装包；编译器从官方签名安装器提取到工作区，下载的安装器签名为 Valid，未把编译器安装到系统。
- 经用户明确批准，在工作区目标目录执行真实当前用户安装（运行数据通过进程 LOCALAPPDATA 重定向到工作区）、运行中覆盖升级及卸载：全部 exit 0。最终安装 EXE 哈希与打包源一致；安装版不含 portable.flag。
- 覆盖升级正常退出运行中的接收端并保留用户设置；卸载移除 EXE、卸载注册项和开始菜单测试组，按设计保留设置。随后清理本次工作区测试配置。
- 未结束共享 ADB 服务。测试接收端已退出，未设置自启动、服务、防火墙或驱动。
- 未重复实测真实 Windows 桌面框选；本次保持既有手势分类和按键序列，并通过应用内手势回归。蓝牙真实电脑配对、Explorer 真正崩溃恢复、多种 Windows DPI 和多用户登录会话尚未实测。

## 分发

- [v1.3.0-preview](https://github.com/FFraankk/MambaPad/releases/tag/v1.3.0-preview) 已发布；标签指向 6fc6aff633aa40932d23f6c0b754c2d337a8c010，5 个附件的远程大小与 SHA256 均已比对一致。

- Android APK／ZIP、Windows 便携 ZIP、安装 EXE 与 SHA256 校验文件作为 Release 附件，产物不进入源码 Git 历史。
- 便携 ZIP 不含运行数据、设备授权或密钥。安装器按当前用户安装；Windows EXE／安装器未签名。
- 两种 Windows 包使用同一接收 EXE，ADB 使用按内容版本划分的目录，升级不强制终止共享 ADB。
