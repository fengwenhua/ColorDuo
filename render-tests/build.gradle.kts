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
tasks.named<JavaCompile>("compileTestJava") {
    options.encoding = "UTF-8"
    source(fileTree(rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo")) {
        include("Prefs.java", "ChargeLayout.java", "TrioSettings.java", "TrioAppearance.java",
            "TrioGeometry.java", "TrioZones.java", "TrioRenderer.java", "ConfigPacket.java",
            "RemoteFontKey.java", "FirstRunDefaults.java", "PreferencesSnapshot.java", "UpdateManifest.java",
            "SettingsSchema.java", "HyperOsIconLease.java", "Refl.java")
    })
}
tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("colorduo.projectRoot", rootProject.projectDir.absolutePath)
    maxHeapSize = "512m"
}
