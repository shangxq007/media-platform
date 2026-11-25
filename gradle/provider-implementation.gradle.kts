import org.gradle.api.tasks.compile.JavaCompile

val compiledProvider = tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory }
val providerIndex = layout.buildDirectory.dir("generated/provider-index")
val indexProviderClasses by tasks.registering {
    inputs.dir(compiledProvider)
    outputs.dir(providerIndex)
    doLast {
        val root = compiledProvider.get().asFile
        val names = root.walkTopDown().filter { it.isFile && it.extension == "class" }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
        require(names.isNotEmpty())
        val index = providerIndex.get().file("META-INF/provider-content/${project.name}").asFile
        index.parentFile.mkdirs()
        index.writeText(names.joinToString("\n", postfix = "\n"))
    }
}
tasks.named<ProcessResources>("processResources") {
    from(indexProviderClasses)
}
