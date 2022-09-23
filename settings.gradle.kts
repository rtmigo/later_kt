rootProject.name = "later"

pluginManagement.resolutionStrategy.eachPlugin {
    if (requested.id.id.startsWith("org.jetbrains.kotlin.")) {
        useVersion("1.6.20")
    }
}
