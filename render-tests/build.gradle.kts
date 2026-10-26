plugins { java }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Desktop shims belong exclusively to this test project, never the Android APK.
sourceSets.test {
    java.srcDir(rootProject.file("tests/shims"))
}
val production = rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo")
val ringOverride = providers.gradleProperty("ringTestProductionSources").orNull?.let { rootProject.file(it) }
val leaseOverride = providers.gradleProperty("leaseTestProductionSources").orNull?.let { rootProject.file(it) }
require(ringOverride == null || leaseOverride == null) { "Choose one alternate render source group at a time" }
val sharedSettings = setOf("Prefs.java", "ChargeLayout.java", "SettingsSchema.java", "TrioSettings.java", "TrioAppearance.java", "TrioGeometry.java")
val overriddenNames = when {
    ringOverride != null -> sharedSettings + setOf("TrioZones.java", "TrioRenderer.java")
    leaseOverride != null -> sharedSettings + setOf("Refl.java", "HyperOsIconLease.java")
    else -> emptySet()
}
val hookSources = leaseOverride ?: production
val bridgeTemplate = layout.projectDirectory.file("src/templates/RingHandoffTest.java.template")
val bridgeGenerated = layout.buildDirectory.dir("generated/handoff-bridge")
val generateHandoffBridge = tasks.register("generateHandoffBridge") {
    inputs.file(hookSources.resolve("TrioHooks.java"))
    inputs.file(bridgeTemplate)
    outputs.dir(bridgeGenerated)
    doLast {
        val hooks = hookSources.resolve("TrioHooks.java").readText()
        fun method(signature: String): String {
            val matches = Regex(Regex.escape(signature) + " \\{.*?\\n    \\}", RegexOption.DOT_MATCHES_ALL).findAll(hooks).toList()
            require(matches.size == 1) { "Handoff source method must be unique: $signature" }
            return matches.single().value
        }
        require(method("private static void reactToIslandChange()").contains("syncKeyguardRows();")) {
            "Island ownership change must refresh lockscreen suppression"
        }
        val delegates = listOf("private static List<String> foldedSlots()", "private static void hideIcon(final View icon)",
            "private static void blockIcons(Object bar)", "private static void applyMeterText(List<View> meters)",
            "private static void syncKeyguardRows()", "private static void showIfHidden(Object view)",
            "private static void hide(Object view)", "private static boolean markCollapsed(View child)")
            .joinToString("\n") { method(it).replace("private static", "static") }
        val target = bridgeGenerated.get().file("io/github/fengwenhua/colorduo/RingHandoffTest.java").asFile
        target.parentFile.mkdirs()
        target.writeText(bridgeTemplate.asFile.readText().replace("// PRODUCTION_METHODS", delegates))
    }
}
sourceSets.test { java.srcDir(bridgeGenerated) }
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(generateHandoffBridge)
    options.encoding = "UTF-8"
    source(listOf("Prefs.java", "ChargeLayout.java", "TrioSettings.java", "TrioAppearance.java",
            "TrioGeometry.java", "TrioZones.java", "TrioRenderer.java", "ConfigPacket.java",
            "RemoteFontKey.java", "FirstRunDefaults.java", "PreferencesSnapshot.java", "UpdateManifest.java",
            "SettingsSchema.java", "HyperOsIconLease.java", "Refl.java").map { name ->
        val directory = if (name in overriddenNames) (ringOverride ?: leaseOverride)!! else production
        directory.resolve(name)
    })
}
tasks.test {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    systemProperty("java.awt.headless", "true")
    systemProperty("colorduo.projectRoot", rootProject.projectDir.absolutePath)
    maxHeapSize = "512m"
}
