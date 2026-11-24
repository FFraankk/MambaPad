# 构建 MambaPad

## 在另一台电脑编译运行

源码包含 Gradle Wrapper 和 Windows 图标资源，不依赖本机工具目录或任何签名备份。首次构建需要联网下载依赖；下载源码 ZIP 后先完整解压，或使用 Git clone。

| 目标 | 准备环境 | 签名要求 |
| --- | --- | --- |
| Android 调试 APK | JDK 21、Android SDK Platform 35 / Build Tools 35.0.0 | 不需要项目密钥，Gradle 自动生成本机 debug 签名 |
| Windows 接收端 EXE | Windows x64、Go 1.25+（CI 使用 1.27.4） | 不需要签名证书；连接手机还需官方 ADB |
| Android 覆盖现有预览版 | 上述 Android 环境，另加原签名密钥 | 或直接下载本仓库 Release / 手动 Release 工作流生成的同签名 APK |
| 完整 Windows 发行包 | Windows 构建环境、ADB、Inno Setup 6.7.4 | 按下文打包；普通编译 EXE 不需要 Inno Setup |

### Android：先生成可运行的调试包

设置当前终端的 JAVA_HOME 与 ANDROID_HOME，指向自己的 JDK 和 SDK；也可在未跟踪的 local.properties 中设置 sdk.dir。准备好 Platform 35、Build Tools 35.0.0 后，在源码根目录执行：

```powershell
.\gradlew.bat --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' assembleDebug testDebugUnitTest
```

APK 位于 app/build/outputs/apk/debug/app-debug.apk。macOS/Linux 使用 `sh ./gradlew`。普通调试无需 GitHub 登录、仓库 Secrets、Python 或加密备份。不要把发布环境的 MAMBAPAD_REQUIRE_SIGNING=true 或部分签名变量带入普通调试终端。

如果手机已安装本项目的预览版，新电脑生成的默认签名通常不同，无法直接覆盖它。需要保留应用数据时，从本仓库 Releases 或 main 的手动 Release 工作流下载同签名产物；要在本地修改后覆盖，才需恢复原密钥并设置下文的四项签名变量。不要通过卸载现有应用来掩盖签名问题。

### Windows：编译后指定 ADB

在 Windows 源码根目录执行（Go 在当前终端可用）：

```powershell
Set-Location windows-receiver
go test ./...
go vet ./...
go build -trimpath '-ldflags=-H=windowsgui -s -w' -o MambaPad.exe .
.\MambaPad.exe -adb '<官方 Platform Tools 目录>\adb.exe'
```

请保留官方 Platform Tools 的配套 DLL 与 adb.exe，不能只复制一个 adb.exe。自编译 EXE 不会自动附带 ADB；正式便携 ZIP 和安装包才已内置它。连接手机仍需 USB 调试授权，个别电脑需要手机厂商驱动。macOS/Linux 可以构建 Android APK，但此 Windows 接收端须在 Windows 上运行，其他桌面平台尚无对应接收端。


Windows 图标资源已包含在源码中；更改图标时才需重新生成。调试命令可选 -console、-dry-run、-listen-only；-quit 请求正在运行的桌面实例正常退出。

## 发布包

Android 包放 APK、LICENSE 和 NOTICE.txt。Windows 包放 EXE、使用说明、LICENSE、NOTICE.txt，并在 `tools/adb` 放官方 Android Platform Tools 中的 `adb.exe`、`AdbWinApi.dll`、`AdbWinUsbApi.dll`、`NOTICE.txt` 和 `source.properties`。同时保留 Go 的 LICENSE；预览包已包含这些文件。

本仓库不包含 SDK、ADB 工具本体、Gradle 缓存、签名密钥或设备调试授权文件。构建脚本不会代替你安装系统工具。正式版本发布前应自行管理发布签名；现有预览 APK 以 debug 密钥签名。


## Windows 两种发行包

使用 Inno Setup 6.7.4 的 ISCC 编译安装包；编译器放在工作区工具目录，脚本不会安装它或修改系统 PATH。准备官方 ADB 工具和 Go LICENSE 后运行：

```powershell
.\scripts\package-windows.ps1 -GoExe 'go' -AdbDirectory '<ADB 目录>' -GoLicense '<Go LICENSE 文件>' -ISCC '<ISCC.exe>'
```

脚本先执行 Go test/vet，再生成 dist/windows-VERSION 下的便携 ZIP、安装 EXE 和 SHA256SUMS.txt。输出目录已存在时拒绝覆盖。安装包不含 portable.flag 或运行数据；便携包包含 portable.flag，运行数据写入旁边的 data。安装版数据写入当前用户 LOCALAPPDATA/MambaPad，卸载保留设置与有限日志。ADB 以不可变版本子目录分发，由 adb-path.txt 指定；不要单独移动 EXE 或删除该文件。

安装、升级和卸载测试会添加或移除当前用户的卸载注册项与开始菜单入口，应先取得相应授权。不得为了测试更改安全开关或结束共享 ADB。

## 本工作区工具与缓存

本地开发约定把新增工具和缓存留在 touchpad 内。当前便携 SDK、Go、ADB 和 Gradle 缓存位于仓库相邻的 tools 目录，不随源码上传。下面只设置当前 PowerShell 进程环境，不修改系统设置：

```powershell
$toolRoot = Join-Path (Split-Path (Get-Location).Path -Parent) 'tools'
$env:ANDROID_HOME = Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-home'
$env:GOCACHE = Join-Path $toolRoot 'go-cache'
$env:GOTMPDIR = Join-Path $toolRoot 'go-tmp'
$env:TEMP = Join-Path $toolRoot 'tmp'
$env:TMP = $env:TEMP
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$($env:TEMP)"
```

运行前核对上述目录已存在；不要无确认安装系统工具。在其他机器上按自己的工作区放置工具，路径不得写进仓库。已有测试 APK 覆盖升级需使用兼容的签名；ANDROID_USER_HOME 下的 debug keystore 也不能提交。

便携 Go 可通过 `& (Join-Path $toolRoot 'go/bin/go.exe') test ./...` 调用，无需写入系统 PATH。依赖已缓存时 Gradle 可加 --offline。

## 真机回归

先完成对应授权与手机解锁，确认是否允许真实电脑输入。测试需要运行接收端和 USB 连接，会旋转应用并发送真实指针移动；手势分类和键盘逻辑的部分断言使用 fake sender，不能据此宣称真实 Windows 行为全通过。

构建测试 APK：

```powershell
.\gradlew.bat --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' assembleDebug assembleDebugAndroidTest
```

测试主包为 `app/build/outputs/apk/debug/app-debug.apk`，辅助包为 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。显式选择设备后安装，手机出现确认时由用户允许；安装被拒绝时不修改系统安全开关来绕过。

```text
adb -s SERIAL shell am instrument -w com.github.ifeel.keysjoy.usb.test/com.github.ifeel.keysjoy.UsbSmokeInstrumentation
```

MIUI 下可能需要将目标 Activity 带到前台。测试完成后按会话约定移除 `com.github.ifeel.keysjoy.usb.test`，保留主包；恢复应用状态，并关闭本次启动的接收端（若用户明确希望保留则遵从）。不要关闭测试前已有的其他服务或使用 kill-server 清理全部 ADB。

选择验证范围见 [CONTRIBUTING.md](CONTRIBUTING.md)，打包与发布见 [docs/RELEASING.md](docs/RELEASING.md)。

## Actions 与统一预览签名

普通 push/PR 运行 Android 构建、JVM 单测和 Windows Go test/vet/build，不读取发布签名、不分发随机证书的 APK。手动运行 Release 工作流会构建四个发行包和 SHA256SUMS.txt，产物保留 7 天；只有推送版本标签才发布。详见 [发布流程](docs/RELEASING.md)。

发布仍使用 debug 构建及现有预览证书，尚未迁移正式签名。本地需要覆盖现有预览版时，在当前进程设置 `MAMBAPAD_KEYSTORE_PATH`、`MAMBAPAD_STORE_PASSWORD`、`MAMBAPAD_KEY_ALIAS`、`MAMBAPAD_KEY_PASSWORD`；四项必须一起提供。`MAMBAPAD_REQUIRE_SIGNING=true` 禁止缺少配置时退回自动生成的 debug 密钥。普通本地开发未设置这些变量时保留 Gradle 默认行为，其 APK 不保证可覆盖已发布版本。

`config/android-signing.sha256` 只保存公开证书指纹。`scripts/ci-release.py android --apksigner <工具路径> --aapt <工具路径>` 校验 APK 签名、包名、versionName/versionCode 后生成 Android 发行包；证书不符即拒绝。不要为了让检查通过而替换可信指纹。
