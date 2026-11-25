# 当前开发交接

最后核对：2026-11-25（Asia/Hong_Kong）。历史变更见 CHANGELOG，待验证项见 ROADMAP。

## 当前发布：1.3.2

- [1.3.2](https://github.com/FFraankk/MambaPad/releases/tag/v1.3.2) 已于 2026-11-25 正式公开并设为 Latest，Android versionCode 12。标签指向 b9b38726674f9d3b6b39dd0c0390d518fcc49521；后续交接文档提交不移动标签，main / develop 同步。
- [Release 45569745062](https://github.com/FFraankk/MambaPad/actions/runs/45569745062) 的 metadata、Android、Windows、汇总发布全部通过；[CI 45569745194](https://github.com/FFraankk/MambaPad/actions/runs/45569745194) 首次运行全部通过。本地 73 项 JVM 测试、Android 主/测试 APK 构建、Go test/vet 和 4 项发布元数据回归通过。
- 五项公开附件已下载至本地忽略目录 dist/verify-1.3.2，独立核验远端摘要/大小、SHA256SUMS、ZIP CRC、LICENSE/NOTICE、Android ZIP 内嵌 APK 一致性；APK 包名、1.3.2 / code 12 及既有证书均正确。Windows EXE 的 vcs.revision 与标签源码一致，vcs.modified=false。
- 功能与真机验证范围见下文。手机仍保留已测试的本地开发包（1.3.1 / code 11，功能源码与本轮一致），正式 1.3.2 下载附件仅完成上述静态校验，未重新安装回归。测试组件已移除，正常 USB 页面已恢复。
- Windows 协议未变，已有 1.3.0 / 1.3.1 接收端仍可使用。APK 沿用 debug 构建/证书，Windows 未签名；未将蓝牙配对、实际系统桌面切换或完整 lint 记为通过。

## 1.3.2 功能与实机验证

- 用户进一步要求加入一键显示桌面按钮和三、四指其他功能。ControlScreen 底部最右侧新增“桌面”，发送 Win+D 显示/恢复桌面，ControlActivity 先取消手势并释放鼠标按键。
- MultiFingerSwipeState 只在两指尚未认定滚动/缩放时接管三、四指；四指增加时重建基线。全部手指共同移动、达到 48dp 阈值且方向明确后，在最后 UP 提交一次。三指左/右切换下/上一个应用，上滑任务视图；四指左/右切换下/上一个桌面，上滑任务视图、下滑显示桌面。三指下滑保留系统截屏，不发应用快捷键。
- 73 项 JVM 测试、Android 主 APK 和测试 APK 构建通过。主 APK 已同签名覆盖安装到 Mi 10，已核对已安装 APK 与本次构建的 SHA-256 一致。用户解锁并确认后，测试组件安装成功，新增手机自动回归已完成。
- `-e shortcuts_only true` 在 Mi 10 通过：八种 USB/蓝牙界面、横竖屏、键盘/触摸板布局的按钮回调、修饰键重置与可见性检查，以及三、四指所有方向、取消、旧双指/拖拽/边缘续移回归。截图确认“桌面”按钮在正常布局及横屏输入法展开时文字完整可见。早期测试借用首页窗口导致输入法平移、测量失真；已改为真实 adjustResize 控制窗口并增加文字可见性断言后通过，未因此更改产品布局。
- 真实 USB 快捷键入口 `-e shortcut_usb_case desktop_button|three_left|three_right|three_up|three_down|four_left|four_right|four_up|four_down` 配合本机 tools/tmp/shortcut_usb_host_test.py 已完成九项检查：Win+D、Alt+Tab、Alt+Shift+Tab、Win+Tab、Ctrl+Win+左右键均收到正确完整的 DOWN/UP 序列，三指下滑为零按键。临时钩子只截获指定注入按键，测试未实际切换用户桌面或应用，不记录物理键盘输入，结束后移除钩子。此结果不是蓝牙实际配对或所有 Windows 应用的实测结论。

## 前一轮双指改进与验证

- 用户在 Windows 多指方案后要求开始实施；前一轮先完成手势统一与双指轻点、滚动、缩放，当时尚未加入三、四指系统快捷键。以下保留该轮实际验证范围，不冒充本轮新增手势已全部实测。
- 新 MultiFingerState 按 pointer ID、质心位移与指间距变化判定手势，模式认定后锁定至手指变化/结束。右键只在全部手指抬起且满足时间/位移限制时触发；多指结束后的残余单指不会接着移动鼠标。
- USB 捏合复用既有键盘能力，发送 Ctrl DOWN → 滚轮 → Ctrl UP，无 Windows 协议变更，原安装版接收端仍可使用。发送异常时 finally 尝试释放 Ctrl；断连/队列溢出沿用接收端释放会话的机制。主蓝牙路径将缩放与键盘动作串行；旧 RelativeMouseSender 不启用缩放。
- 60 项 JVM 测试及 APK/测试 APK 构建通过。Mi 10 的 `-e multi_finger_only true` 模拟发送器回归通过，覆盖双指模式互斥、最后抬指才右键、长按/取消/增减指不误点、旧有单双击拖拽、指针变化及边缘续移。
- 真实 USB → 原安装版 Windows 接收端 → 临时原生 RichEdit 文档通过：双指右键一次；普通滚动将首可见行由 50 移至 44，不带 Ctrl；捏合将文档放大至 150%，结束后 Ctrl 释放。独立 Edge 配置与临时本地页面通过浏览器实际缩放（DPR 1.5 → 1.875），结束后 Ctrl 释放。未触及用户文档或既有浏览器标签。
- 真实 Windows 原生 ListView 边缘拖拽回归通过：普通滑动无续移，拖拽框选 4 项，前 120ms 有 6 次实际续移，松手后位移 0。其他 DPI、Photos 等图片应用与蓝牙真实配对尚未验证；不把模拟发送器或 USB 结果写成蓝牙端到端通过。
- 手机已同签名覆盖安装本轮本地开发包（仍为 versionCode 11 / 1.3.1，与公开附件不同），保留数据。临时测试组件/窗口收尾后移除，恢复正常 USB 控制页面；正式 1.3.1 Release 保持不变。
- 用户确认这台小米手机的三指下滑用于系统截屏。后续三指方案需考虑系统拦截与 ACTION_CANCEL，不能假定所有手机都把完整手势交给应用；本轮未更改手机系统手势或安全设置。

## 上一版本发布：1.3.1

- [1.3.1](https://github.com/FFraankk/MambaPad/releases/tag/v1.3.1) 已于 2026-11-25 正式公开并设为 Latest，Android versionCode 11，标签指向 8d913785758f4f315c3908e271da302b13c29936。main / develop 已同步；后续交接文档提交不移动发布标签。
- 新增拖拽/框选边缘续移：工具栏左键按住或轻点后再次按住滑动，进入 16dp 边缘带即沿原方向慢速续移，无停留等待。普通滑动、右键按住和直接按在边缘不触发。
- 实现位于 EdgeMotionState / ViewListener，ControlActivity 区分左键与右键按住。Windows 协议和接收端功能未改，已有 1.3.0 Windows 接收端可继续使用。
- 保持已有 debug 兼容签名；Windows EXE/安装包未签名。主要支持 USB＋Windows，蓝牙兼容性仍在完善。旧 Release 和标签不覆盖、不移动。
- [Release 45557492103](https://github.com/FFraankk/MambaPad/actions/runs/45557492103) 的 metadata、Android、Windows、附件汇总和公开发布全部通过；修复后的草稿查询已完成新标签自动发布验证。
- [普通 CI 45557491730](https://github.com/FFraankk/MambaPad/actions/runs/45557491730) 首次在 Maven 依赖解析阶段失败，只重试失败任务后第二次全部通过，无代码或标签变动。文档 CI 路径过滤提交 d2b188f 已一并推送，发布工作流不受其影响。
- 五项公开附件均已下载，逐项核对远端 SHA-256/大小、SHA256SUMS、ZIP CRC、许可文件、Android ZIP 内 APK 一致性、APK 包名/版本/可信证书。Windows EXE 的 vcs.revision 与标签源码提交一致，vcs.modified=false。已补充本轮功能与验证范围的发布说明。

## 边缘续移验证

- 36 项 JVM 状态回归、Android APK 和测试 APK 构建通过。Mi 10 实际执行 `-e edge_motion_only true` 通过：覆盖旧有手势、普通滑动及右键不续移、左键拖拽、抬手/松键/反向/取消/多指/尺寸变化停止；此路径使用模拟发送器。
- 真实 USB → 当前安装版 Windows 接收端 → 临时原生 ListView 右边缘回归通过：普通滑动边缘停留无续移或点击；轻点后再次按住形成一次 DOWN/UP；边缘前 120ms 采样到 7 次实际位移，持续阶段 27 次，框选 4 个临时项目，松手后位移 0。
- 上述真机功能测试使用 versionCode 10 / 1.3.0 的本地开发包，已同签名覆盖安装、核对已安装 APK 的 SHA-256；测试组件已移除，手机恢复 USB 控制页面。新 1.3.1 正式附件尚未执行真机回归，不将功能源码验证等同于正式附件验证。
- 真实 Windows 测试仅覆盖右边缘；其他边缘、其他 DPI、其他手机、主观手感和蓝牙端到端尚待验证。完整 Android lint 的既有问题未修复或屏蔽。
- 临时宿主脚本与采样结果仅留在本机 tools/tmp，未加入源码。`-e edge_usb_case ordinary|drag` 会发送真实输入，必须先聚焦并守护自己的临时选择测试窗口，不可对用户桌面直接运行。MIUI 后台启动受限时可显式启动同一目标 Activity 到前台，不修改安全设置。

## 既有验证边界

- 1.3.0 的输入法、复制/粘贴、触觉、原生 Windows 窗口和托盘验证记录保留在 CHANGELOG、docs/desktop-haptics-1.3.md。旧版的 20 项 JVM 与完整 instrumentation 不冒充新版全量真机回归。
- 先前的安装/覆盖/卸载和设置保留验证来自 1.3.0-preview，未将其改写为新版安装包已测。真实电脑剪贴板、真实震动、多 DPI、Explorer 崩溃恢复未全面验证。
- 本轮临时窗口均已关闭，保留原先运行的安装版接收端；不终止共享 ADB，不修改系统配置，不清除用户数据。
- 工具、凭据、签名和构建产物不进入 Git。签名离线恢复方式见 RELEASING；正式 Android 签名迁移与 Windows 代码签名尚未实施。

## 下一步范围

- 三、四指与桌面按钮已按用户本轮授权实现并完成本轮手机及受控 Windows 快捷键回归。系统三指截屏设置不变，应用不抢占三指下滑；测试组件与窗口已收尾移除，手机恢复正常 USB 控制页面。本轮不自动提交或发布这次新功能。
