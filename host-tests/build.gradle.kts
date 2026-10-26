plugins { java }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
dependencies { testImplementation("junit:junit:4.13.2") }

val glyphSources = rootProject.file(providers.gradleProperty("hostTestProductionSources").orNull
    ?: "app/src/main/java/io/github/fengwenhua/colorduo")
val productionHooks = glyphSources.resolve("TrioHooks.java")
val hookTemplate = layout.projectDirectory.file("src/templates/TrioHooks.java.template")
val generated = layout.buildDirectory.dir("generated/hook-fixture")
val presenceSources = layout.buildDirectory.dir("generated/presence-fixtures")
val generateDrawHookFixture = tasks.register("generateDrawHookFixture") {
    inputs.file(productionHooks)
    inputs.file(hookTemplate)
    outputs.dir(generated)
    doLast {
        // Compile the live interception body rather than maintaining another routing implementation.
        val marker = "\"colorduo-draw\", new XposedInterface.Hooker() {"
        val text = productionHooks.readText()
        val start = text.indexOf(marker)
        require(start >= 0 && text.indexOf(marker, start + 1) == -1) { "Production draw hook must be found exactly once" }
        val bodyStart = start + marker.length
        val end = text.indexOf("\n                });", bodyStart)
        require(end > bodyStart) { "Production draw hook boundary missing" }
        val output = generated.get().file("io/github/fengwenhua/colorduo/TrioHooks.java").asFile
        output.parentFile.mkdirs()
        output.writeText(hookTemplate.asFile.readText().replace("/* PRODUCTION_DRAW_HOOK */", text.substring(bodyStart, end)))
    }
}
val generatePresenceFixtures = tasks.register("generatePresenceFixtures") {
    val production = rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo")
    val readoutHooks = production.resolve("TrioHooks.java")
    val templates = layout.projectDirectory.dir("src/templates")
    inputs.files(production.resolve("WifiPresence.java"), production.resolve("BatteryPresence.java"), readoutHooks)
    inputs.files(templates.file("WifiPresenceTest.java.template"), templates.file("BatteryPresenceTest.java.template"))
    outputs.dir(presenceSources)
    doLast {
        fun method(file: java.io.File, signature: String): String {
            val matches = Regex(Regex.escape(signature) + " \\{.*?\\n    \\}", RegexOption.DOT_MATCHES_ALL)
                .findAll(file.readText()).toList()
            require(matches.size == 1) { "Production method must be found exactly once: $signature" }
            return matches.single().value
        }
        fun emit(name: String, replacements: Map<String, String>) {
            var text = templates.file("$name.java.template").asFile.readText()
            replacements.forEach { (marker, value) ->
                require(text.contains(marker)) { "Missing template marker $marker" }
                text = text.replace(marker, value)
            }
            val file = presenceSources.get().file("io/github/fengwenhua/colorduo/$name.java").asFile
            file.parentFile.mkdirs(); file.writeText(text)
        }
        emit("WifiPresenceTest", mapOf(
            "@RESOLVE@" to method(production.resolve("WifiPresence.java"), "static boolean resolve(boolean iconVisible, int cachedLevel)"),
            "@PUBLISH@" to method(production.resolve("WifiPresence.java"), "private static void publish(boolean next)")))
        val separator = Regex("static final char PERF_BREAK = '(.*?)';").find(readoutHooks.readText())
            ?: error("Production performance separator missing")
        emit("BatteryPresenceTest", mapOf(
            "@WATTS@" to method(production.resolve("BatteryPresence.java"), "private static float wattsOf(int currentMicroAmps, int voltageMilliVolts)"),
            "@PUBLISH@" to method(production.resolve("BatteryPresence.java"), "private static void publish(boolean attached, float watts, int temperature)"),
            "@POWER_TEXT@" to method(readoutHooks, "private static String powerText(float watts)"),
            "@PERF_TEXT@" to method(readoutHooks, "private static String perfText(TrioAppearance a)"),
            "@PERF_LINES@" to method(readoutHooks, "private static int perfLines(String text)"),
            "@PERF_BREAK@" to separator.groupValues[1]))
    }
}
sourceSets.test { java.srcDir(generated); java.srcDir(presenceSources) }
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(generateDrawHookFixture)
    dependsOn(generatePresenceFixtures)
    options.encoding = "UTF-8"
    source(fileTree(glyphSources) {
        include("GlyphDrawable.java", "Refl.java")
    })
    source(rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo/HyperOsReadout.java"))
}
tasks.test {
    maxHeapSize = "128m"
    systemProperty("colorduo.productionSources", rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo").absolutePath)
    systemProperty("colorduo.glyphSources", glyphSources.absolutePath)
}

// Performance layout uses different Paint/View contracts, isolated from native-slot fixtures.
val readout = sourceSets.create("readoutTest")
configurations.named(readout.implementationConfigurationName) { extendsFrom(configurations.testImplementation.get()) }
configurations.named(readout.runtimeOnlyConfigurationName) { extendsFrom(configurations.testRuntimeOnly.get()) }
val readoutTemplates = layout.projectDirectory.dir("src/readoutTest/templates")
val readoutGenerated = layout.buildDirectory.dir("generated/readout-fixtures")
val generateReadoutFixtures = tasks.register("generateReadoutFixtures") {
    val production = rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo")
    val files = mapOf("hooks" to "TrioHooks.java", "rend" to "TrioRenderer.java", "geom" to "TrioGeometry.java",
        "appear" to "TrioAppearance.java", "prefs" to "Prefs.java")
    inputs.files(files.values.map { production.resolve(it) })
    inputs.dir(readoutTemplates)
    outputs.dir(readoutGenerated)
    doLast {
        val values = readoutTemplates.file("bindings.tsv").asFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val fields = line.split('\t', limit = 4)
                require(fields.size == 4) { "Malformed readout fixture binding" }
                val (name, alias, group, pattern) = fields
                val matches = Regex(pattern).findAll(production.resolve(files.getValue(alias)).readText()).toList()
                require(matches.size == 1) { "Readout source binding must be unique: $name" }
                name to matches.single().groupValues[group.toInt()]
            }
        readoutTemplates.asFile.walkTopDown().filter { it.isFile && it.name.endsWith(".java.template") }.forEach { template ->
            var text = template.readText()
            values.forEach { (name, value) -> text = text.replace("@$name@", value) }
            require(!Regex("@[A-Za-z][A-Za-z0-9]*@").containsMatchIn(text)) { "Unresolved readout marker in ${template.name}" }
            val target = readoutGenerated.get().asFile.resolve(template.relativeTo(readoutTemplates.asFile).path.removeSuffix(".template"))
            target.parentFile.mkdirs(); target.writeText(text)
        }
    }
}
readout.java.srcDir(readoutGenerated)
tasks.named<JavaCompile>(readout.compileJavaTaskName) {
    dependsOn(generateReadoutFixtures)
    options.encoding = "UTF-8"
    source(rootProject.file("tests/shims/android/graphics/Typeface.java"),
        rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo/HyperOsReadout.java"))
}
val readoutTest = tasks.register<Test>("readoutTest") {
    description = "Run isolated performance sizing and reservation regressions"
    group = "verification"
    testClassesDirs = readout.output.classesDirs
    classpath = readout.runtimeClasspath
    dependsOn(tasks.named(readout.classesTaskName))
    maxHeapSize = "128m"
    systemProperty("java.awt.headless", "true")
    systemProperty("colorduo.productionSources", rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo").absolutePath)
}
tasks.named("test") { dependsOn(readoutTest) }

// OS4 strip reservation and island layout need their own View geometry model.
val strip = sourceSets.create("stripTest")
configurations.named(strip.implementationConfigurationName) { extendsFrom(configurations.testImplementation.get()) }
configurations.named(strip.runtimeOnlyConfigurationName) { extendsFrom(configurations.testRuntimeOnly.get()) }
val stripTemplates = layout.projectDirectory.dir("src/stripTest/templates")
val stripGenerated = layout.buildDirectory.dir("generated/strip-fixtures")
val stripSources = rootProject.file(providers.gradleProperty("stripTestProductionSources").orNull
    ?: "app/src/main/java/io/github/fengwenhua/colorduo")
val generateStripFixtures = tasks.register("generateStripFixtures") {
    val files = mapOf("hooks" to "TrioHooks.java", "prefs" to "Prefs.java")
    inputs.files(files.values.map { stripSources.resolve(it) })
    inputs.dir(stripTemplates)
    outputs.dir(stripGenerated)
    doLast {
        val values = stripTemplates.file("bindings.tsv").asFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val fields = line.split('\t', limit = 4)
                require(fields.size == 4) { "Malformed strip fixture binding" }
                val (name, alias, group, pattern) = fields
                val matches = Regex(pattern).findAll(stripSources.resolve(files.getValue(alias)).readText()).toList()
                require(matches.size == 1) { "Strip source binding must be unique: $name" }
                name to matches.single().groupValues[group.toInt()]
            }
        stripTemplates.asFile.walkTopDown().filter { it.isFile && it.name.endsWith(".java.template") }.forEach { template ->
            var text = template.readText()
            values.forEach { (name, value) -> text = text.replace("@$name@", value) }
            require(!Regex("@[A-Za-z][A-Za-z0-9]*@").containsMatchIn(text)) { "Unresolved strip marker in ${template.name}" }
            val target = stripGenerated.get().asFile.resolve(template.relativeTo(stripTemplates.asFile).path.removeSuffix(".template"))
            target.parentFile.mkdirs(); target.writeText(text)
        }
    }
}
strip.java.srcDir(stripGenerated)
tasks.named<JavaCompile>(strip.compileJavaTaskName) {
    dependsOn(generateStripFixtures)
    options.encoding = "UTF-8"
    source(stripSources.resolve("OutRingLayout.java"), stripSources.resolve("Refl.java"))
}
val stripTest = tasks.register<Test>("stripTest") {
    description = "Run isolated OS4 outside strip reservation and island layout regressions"
    group = "verification"
    testClassesDirs = strip.output.classesDirs
    classpath = strip.runtimeClasspath
    dependsOn(tasks.named(strip.classesTaskName))
    maxHeapSize = "128m"
}
tasks.named("test") { dependsOn(stripTest) }

// Historical issue regressions use telephony/tint/clock View contracts of their own.
val issue = sourceSets.create("issueTest")
configurations.named(issue.implementationConfigurationName) { extendsFrom(configurations.testImplementation.get()) }
configurations.named(issue.runtimeOnlyConfigurationName) { extendsFrom(configurations.testRuntimeOnly.get()) }
val issueTemplates = layout.projectDirectory.dir("src/issueTest/templates")
val issueGenerated = layout.buildDirectory.dir("generated/issue-fixtures")
val issueSources = rootProject.file(providers.gradleProperty("issueTestProductionSources").orNull
    ?: "app/src/main/java/io/github/fengwenhua/colorduo")
val generateIssueFixtures = tasks.register("generateIssueFixtures") {
    val files = mapOf("hooks" to "TrioHooks.java", "state" to "TrioState.java")
    inputs.files(files.values.map { issueSources.resolve(it) })
    inputs.dir(issueTemplates)
    outputs.dir(issueGenerated)
    doLast {
        val values = issueTemplates.file("bindings.tsv").asFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val fields = line.split('\t', limit = 4)
                require(fields.size == 4) { "Malformed issue fixture binding" }
                val (name, alias, group, pattern) = fields
                val matches = Regex(pattern).findAll(issueSources.resolve(files.getValue(alias)).readText()).toList()
                require(matches.size == 1) { "Issue source binding must be unique: $name" }
                name to matches.single().groupValues[group.toInt()]
            }
        issueTemplates.asFile.walkTopDown().filter { it.isFile && it.name.endsWith(".java.template") }.forEach { template ->
            var text = template.readText()
            values.forEach { (name, value) -> text = text.replace("@$name@", value) }
            require(!Regex("@[A-Za-z][A-Za-z0-9]*@").containsMatchIn(text)) { "Unresolved issue marker in ${template.name}" }
            val target = issueGenerated.get().asFile.resolve(template.relativeTo(issueTemplates.asFile).path.removeSuffix(".template"))
            target.parentFile.mkdirs(); target.writeText(text)
        }
    }
}
issue.java.srcDir(issueGenerated)
tasks.named<JavaCompile>(issue.compileJavaTaskName) {
    dependsOn(generateIssueFixtures)
    options.encoding = "UTF-8"
    source(issueSources.resolve("TrioZones.java"), issueSources.resolve("TrioTint.java"), issueSources.resolve("OutRingLayout.java"))
    source(rootProject.file("tests/shims/android/content/SharedPreferences.java"), rootProject.file("tests/shims/android/os/Bundle.java"))
    source(fileTree(rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo")) {
        include("Prefs.java", "ChargeLayout.java", "SettingsSchema.java", "TrioSettings.java", "TrioAppearance.java", "TrioGeometry.java", "CapsuleLayoutSettings.java", "ContentSettings.java")
    })
}
val issueTest = tasks.register<Test>("issueTest") {
    description = "Run isolated signal, tint, clock and historical issue regressions"
    group = "verification"
    testClassesDirs = issue.output.classesDirs
    classpath = issue.runtimeClasspath
    dependsOn(tasks.named(issue.classesTaskName))
    maxHeapSize = "128m"
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    systemProperty("colorduo.projectRoot", rootProject.projectDir.absolutePath)
    systemProperty("colorduo.issueSources", issueSources.absolutePath)
}
tasks.named("test") { dependsOn(issueTest) }
