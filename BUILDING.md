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
