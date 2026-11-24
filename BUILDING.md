# 构建 MambaPad

## Android

需要 JDK 17 或更新版本、Android SDK Platform 35 和相应 Build Tools。设置 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir`；本地路径文件不要提交。

```powershell
.\gradlew.bat --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' assembleDebug testDebugUnitTest
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。macOS/Linux 可使用 `sh ./gradlew`。首次构建需要联网获取 Gradle 与依赖。Gradle Wrapper、AGP 与 Kotlin 版本已在项目中声明。

`lintDebug` 当前仍有 41 个旧代码静态检查错误；构建与 JVM 单测可以独立运行。设备测试 runner 位于 `app/src/androidTest`，需要 Android 真机及临时测试 APK，执行时会进行应用旋转和触控手势。

## Windows 接收端

需要 Windows x64 与 Go 1.25 或更新版本；只使用 Go 标准库。

```powershell
cd windows-receiver
go test ./...
go vet ./...
go build -trimpath '-ldflags=-s -w' -o MambaPad.exe .
```

`rsrc_windows_amd64.syso` 是已生成的 Windows 图标资源，原始图标在 `branding/mambapad.ico`。首个验证目标为 Windows x64，其他架构未完成测试。

## 发布包

Android 包放 APK、LICENSE 和 NOTICE.txt。Windows 包放 EXE、使用说明、LICENSE、NOTICE.txt，并在 `tools/adb` 放官方 Android Platform Tools 中的 `adb.exe`、`AdbWinApi.dll`、`AdbWinUsbApi.dll`、`NOTICE.txt` 和 `source.properties`。同时保留 Go 的 LICENSE；预览包已包含这些文件。

本仓库不包含 SDK、ADB 工具本体、Gradle 缓存、签名密钥或设备调试授权文件。构建脚本不会代替你安装系统工具。正式版本发布前应自行管理发布签名；现有预览 APK 以 debug 密钥签名。


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
