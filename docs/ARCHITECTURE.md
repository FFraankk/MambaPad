# 架构与关键边界

## 总体路径

Android 首页 → ControlActivity → 共用 ControlScreen。

触控事件 → ViewListener → TapDragState / 指针跟踪 → MouseInputSender。

- USB：UsbMouseSender / RemoteInputEvent → AdbSocketTransport → ADB reverse → Windows loopback 接收端 → InputState → WindowsInjector → SendInput。
- 蓝牙：BluetoothInputSender → BluetoothController 提供的 HID 会话 → 电脑的标准鼠标/键盘 HID 支持。
- 文字：RemoteImeEditor → TextDelta → 已提交文字 / 功能键 → USB Unicode 或 HID 按键。

USB 协议细节见 [protocol-v1.md](protocol-v1.md)。包名仍沿用 com.github.ifeel.keysjoy.usb；显示品牌为 MambaPad。它们不是同一个层面的命名，修改包名会影响覆盖升级与数据保留。

## 手势职责

ViewListener 处理 Android MotionEvent、系统双击窗口和触摸阈值；TapDragState 只做无 Android 依赖的状态分类，返回 CLICK / DOWN / UP。

第一下轻点暂缓确认；第二下移动越过阈值，输出单次 DOWN；松开输出 UP。第二下不移动而松开，输出两次 CLICK。单击的等待不能通过“先点一下，再补拖动”消除：Windows 的 ListView 会把快速第二下变成双击消息，桌面框选流程与文字编辑器不同。

DragPointerTracker 按 pointer ID 跟踪，指针增加/抬起时只建立新基线；不能把另一个手指坐标当成原手指位移。滚轮按钮与触控区使用独立基线。

边缘续移由无 Android 依赖的 EdgeMotionState 判断：使用 dp 坐标，需先经过内部区域，再朝外滑入 16dp 边缘带。最近约 100ms 的有效位移决定方向，进入边缘即以每秒 80 个相对移动单位续移，无停留等待。ViewListener 在主线程每约 16ms 更新，小数位移独立累积，单次时间步长限制为 32ms，避免主线程卡顿后的跳动。仅已识别的轻点拖拽或工具栏左键按住时启用；普通移动和右键按住不触发，续移只发送 MOVE。

抬手、松开左键、取消、多指变化和模式切换会清除续移；反向移动立即解除触发。会话取消沿用 cancelAndRelease；定时回调另检查 View 是否仍可见、已附着且尺寸不变，布局变化后需重新触摸。直接在边缘按下不会触发，双指滚动和缩放不参与续移。

## 传输与状态

AdbSocketTransport 的工作线程拥有 socket、序列号和有界队列；UI 不直接写 socket。连接成功后协商 mouse / keyboard / text 能力，不能给旧接收端发送它未声明支持的事件。断线清队列，不重放旧事件。

Windows 每个连接的 InputState 跟踪本会话按下的鼠标键、普通键和修饰键。结束时先释放普通键，再释放修饰键与鼠标键。Win32 键盘扫描码使用显式映射，不能直接把 HID usage 当作虚拟键码。

Go 接收端只依赖标准库；ADB 是随包的独立工具，启动、reverse 所有权和清理由 adb_windows.go 管理。不要删除别的应用或用户拥有的端口映射，也不使用 kill-server 作为常规退出流程。

## 输入法与 UI

系统输入法的 composing 阶段留在本地，提交后才计算文字差分。USB 以 Unicode 输入已确认文本，回车/Tab 和快捷键走按键事件；蓝牙只有 HID 按键通道。临时输入上下文不持久化，不通过剪贴板中转。

竖屏使用窗口剩余高度容纳触控板、鼠标键、功能栏和输入框，不预设键盘高度。悬浮键盘覆盖范围由输入法控制。横屏纯触控尽量使用完整宽度，键盘模式两侧按键、中间触控板。

ControlActivity 负责连接、暂停/销毁和返回；离开时取消手势、释放按键并结束会话，迟到的回调不能复活旧连接。

## 触觉反馈与 Windows 桌面壳

TouchFeedback 统一读取 control_ui 中的总开关，使用系统 View 触觉反馈并尊重系统设置。ViewListener 在轻点抬起、双击的第二次抬起及拖动成立时反馈；反馈不等待远端单击确认，不改变 DOWN/MOVE/UP 时序。左右键按下和共享键盘有效操作各反馈一次，系统输入法管理自身震动。

Windows desktop_windows.go 在固定 OS 线程运行 Win32 消息循环、托盘和窗口；app.go 管理有锁状态、配置、有限日志及接收会话。后台状态更新通过窗口消息回到 UI 线程。设备切换先取消旧会话、释放按键并清理自有 reverse，再启动新会话。关闭／最小化窗口隐藏到托盘；退出等待接收与 ADB 清理，Explorer 重建任务栏后补回图标。

便携版与安装版共用当前登录会话的命名互斥量；第二次启动恢复原窗口。portable.flag 决定数据目录，安装版使用当前用户 LOCALAPPDATA。packaging/windows 与 scripts/package-windows.ps1 生成两种包，安装器调用 -quit 正常结束已有接收端，不结束共享 ADB。

## 历史代码

SelectDeviceActivity、部分 sender 和键盘布局来自旧 KeysJoy 页面。当前首页走共享控制页，旧文件仍可能被引用；删除前应检查调用、资源与蓝牙依赖。蓝牙真实配对兼容性尚未全面验证，不能把现有 UI 可用等同于无线功能已稳定。
