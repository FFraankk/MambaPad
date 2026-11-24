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

## 传输与状态

AdbSocketTransport 的工作线程拥有 socket、序列号和有界队列；UI 不直接写 socket。连接成功后协商 mouse / keyboard / text 能力，不能给旧接收端发送它未声明支持的事件。断线清队列，不重放旧事件。

Windows 每个连接的 InputState 跟踪本会话按下的鼠标键、普通键和修饰键。结束时先释放普通键，再释放修饰键与鼠标键。Win32 键盘扫描码使用显式映射，不能直接把 HID usage 当作虚拟键码。

Go 接收端只依赖标准库；ADB 是随包的独立工具，启动、reverse 所有权和清理由 adb_windows.go 管理。不要删除别的应用或用户拥有的端口映射，也不使用 kill-server 作为常规退出流程。

## 输入法与 UI

系统输入法的 composing 阶段留在本地，提交后才计算文字差分。USB 以 Unicode 输入已确认文本，回车/Tab 和快捷键走按键事件；蓝牙只有 HID 按键通道。临时输入上下文不持久化，不通过剪贴板中转。

竖屏使用窗口剩余高度容纳触控板、鼠标键、功能栏和输入框，不预设键盘高度。悬浮键盘覆盖范围由输入法控制。横屏纯触控尽量使用完整宽度，键盘模式两侧按键、中间触控板。

ControlActivity 负责连接、暂停/销毁和返回；离开时取消手势、释放按键并结束会话，迟到的回调不能复活旧连接。

## 历史代码

SelectDeviceActivity、部分 sender 和键盘布局来自旧 KeysJoy 页面。当前首页走共享控制页，旧文件仍可能被引用；删除前应检查调用、资源与蓝牙依赖。蓝牙真实配对兼容性尚未全面验证，不能把现有 UI 可用等同于无线功能已稳定。
