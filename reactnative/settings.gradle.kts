@file:Suppress("UnstableApiUsage")

val yarn = providers.exec { commandLine("which", "yarn") }.standardOutput.asText.get().trim()

providers.exec {
    workingDir(layout.settingsDirectory)
    commandLine(yarn, "install")
}.result.get().assertNormalExitValue()

rootProject.name = "reactnative"

includeBuild("android")
