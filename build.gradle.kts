plugins {
    id("com.android.application") version "9.3.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
}

tasks.register<Exec>("verifyUiResources") {
    group = "verification"
    description = "Checks XML layouts and bilingual resources (Windows PowerShell)."
    workingDir(rootDir)
    commandLine("powershell.exe", "-NoProfile", "-File", rootProject.file("tools/Test-UiResources.ps1").absolutePath)
}

