# PocketShare 1.3.6

Android 局域网 SMB 文件共享应用，使用内置 ARM64 Samba 运行时。应用最低支持 Android 9（API 28）；完整共享功能需要 ARM64 Root 设备。

## 构建

在 Android Studio 中打开本目录。配置兼容 AGP 9.3.3 的 IDE、JDK 21 和 Android SDK Platform 35；Gradle Wrapper 为 9.5.0，Kotlin 插件为 2.2.10。以上版本以随附构建配置为准。

Windows 可运行 `./tools/setup-android-studio.ps1` 配置本机环境，或设置 JAVA_HOME、ANDROID_HOME。首次构建需要下载 Gradle 和 Maven 依赖。

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Linux/macOS 使用 `sh ./gradlew assembleDebug` 或 `sh ./gradlew assembleRelease`。

无签名材料时，release 输出未签名 APK，位于 `app/build/outputs/apk/release/`，需要自行签名才能安装。debug 使用本机调试签名。自有发布证书可放入 `signing/pocketshare-release.p12`（PKCS12，别名 pocketshare），通过环境变量 POCKETSHARE_SIGNING_PASSWORD 传入密码，再构建 release。也可在 Android Studio 中使用 Generate Signed App Bundle / APK。请勿提交证书或密码；新证书不能覆盖其他证书签名的应用。

## 目录

- `app/src/main/java/io/pocketshare/`：应用逻辑。
- `app/src/main/res/`：界面、图标及中英文资源。
- `app/src/main/assets/`：原生运行库与已有第三方许可文件。
- `tools/`：环境配置、验证脚本、自定义 C 源码及原生组件维护脚本。
- `gradle/`：Gradle Wrapper。

Windows 资源检查：`powershell -NoProfile -ExecutionPolicy Bypass -File tools/Test-UiResources.ps1`。

普通 APK 构建无需重新编译 Samba。原生维护脚本需要另行准备 NDK r27c、匹配的 Samba 4.24.7 源码及 Termux 依赖；脚本中的 /opt 路径属于维护环境约定，并非全自动工具链安装流程。

连接方式以应用显示的地址为准。Windows 文件资源管理器应通过标准 TCP 445 端口访问 `\\设备IP\共享名`。

发布范围、许可状态及验证结果见 [SOURCE_RELEASE.md](SOURCE_RELEASE.md)。
