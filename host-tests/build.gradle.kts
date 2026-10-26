plugins { java }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
dependencies { testImplementation("junit:junit:4.13.2") }

val productionHooks = rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo/TrioHooks.java")
val hookTemplate = layout.projectDirectory.file("src/templates/TrioHooks.java.template")
val generated = layout.buildDirectory.dir("generated/hook-fixture")
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
sourceSets.test { java.srcDir(generated) }
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(generateDrawHookFixture)
    options.encoding = "UTF-8"
    source(fileTree(rootProject.file("app/src/main/java/io/github/fengwenhua/colorduo")) {
        include("GlyphDrawable.java", "Refl.java")
    })
}
tasks.test { maxHeapSize = "128m" }
