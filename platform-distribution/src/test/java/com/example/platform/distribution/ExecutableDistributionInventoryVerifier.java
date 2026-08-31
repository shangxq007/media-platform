package com.example.platform.distribution;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Repository/build/deployment-fact-derived executable distribution inventory. */
final class ExecutableDistributionInventoryVerifier {

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("usage: ExecutableDistributionInventoryVerifier <repository-root>");
        }
        InventoryReport report = new ExecutableDistributionInventoryVerifier()
                .inspect(Path.of(arguments[0]));
        report.discovered().forEach(fact -> System.out.println(
                fact.kind() + "\t" + fact.source() + "\t" + fact.detail()));
        System.out.println("EXECUTABLE_DISTRIBUTION_COUNT=" + report.discovered().size());
        System.out.println("EXECUTABLE_DISTRIBUTION_CLASSIFIED_COUNT=" + report.classified().size());
        System.out.println("UNCLASSIFIED_EXECUTABLE_DISTRIBUTION_COUNT=" + report.unclassified().size());
        report.requireComplete();
    }

    enum Classification {
        CANONICAL_APPLICATION_RUNTIME,
        WORKER_RUNTIME,
        SANDBOX_RUNTIME,
        TEST_ONLY_EXECUTABLE,
        BUILD_ONLY_TOOL,
        DISABLED_NONPRODUCTION,
        OTHER_EXPLICIT
    }

    enum FactKind {
        INCLUDED_BOOT_JAR_PROJECT,
        BUILD_DECLARED_LAUNCH,
        PRODUCTION_MAIN_CLASS,
        DOCKER_LAUNCH,
        COMPOSE_SERVICE_LAUNCH,
        DEPLOYMENT_CONTAINER,
        LAUNCH_SCRIPT,
        BUILT_EXECUTABLE_ARTIFACT
    }

    enum ScanArea {
        SETTINGS_AND_INCLUDED_PROJECTS,
        GRADLE_BOOT_JAR_APPLICATION_AND_JAVA_EXEC,
        PRODUCTION_MAIN_CLASSES,
        DOCKERFILES,
        COMPOSE_FILES,
        DEPLOYMENT_MANIFESTS,
        WORKER_SANDBOX_AND_SUPPORT_SCRIPTS,
        BUILT_EXECUTABLE_ARTIFACTS
    }

    record ExecutableFact(FactKind kind, String source, String detail) {
        ExecutableFact {
            if (kind == null || source == null || source.isBlank()
                    || detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("executable facts must be complete");
            }
        }

        String searchableText() {
            return (source + " " + detail).toLowerCase(Locale.ROOT);
        }
    }

    record ClassifiedExecutable(ExecutableFact fact, Classification classification) {}

    record InventoryReport(
            List<ExecutableFact> discovered,
            List<ClassifiedExecutable> classified,
            List<ExecutableFact> unclassified,
            Set<ScanArea> inspectedAreas) {

        void requireComplete() {
            if (discovered.size() != classified.size() || !unclassified.isEmpty()) {
                throw new IllegalStateException(
                        "Executable distribution inventory is incomplete: discovered="
                                + discovered.size() + ", classified=" + classified.size()
                                + ", unclassified=" + unclassified);
            }
        }
    }

    private static final Pattern QUOTED_PROJECT = Pattern.compile(
            "[\\\"'](:?[A-Za-z0-9_-]+(?:[:][A-Za-z0-9_-]+)*)[\\\"']");
    private static final Pattern JAVA_MAIN = Pattern.compile(
            "public\\s+static\\s+void\\s+main\\s*\\(|fun\\s+main\\s*\\(");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern JAVA_TYPE = Pattern.compile(
            "(?:public\\s+)?(?:final\\s+)?(?:class|record|enum)\\s+([A-Za-z0-9_]+)");
    private static final String SPRING_BOOT_JAR_LAUNCHER =
            "org.springframework.boot.loader.launch.JarLauncher";
    private static final Map<String, Classification> KNOWN_JAVA_DISTRIBUTIONS = Map.ofEntries(
            Map.entry(
                    "settings.gradle.kts|includedProject=platform-app bootJarTask=implicit "
                            + "build=platform-app/build.gradle.kts",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            Map.entry(
                    "settings.gradle.kts|includedProject=platform-distribution bootJarTask=implicit "
                            + "build=platform-distribution/build.gradle.kts",
                    Classification.OTHER_EXPLICIT),
            Map.entry(
                    "settings.gradle.kts|includedProject=remote-render-worker bootJarTask=implicit "
                            + "build=remote-render-worker/build.gradle.kts",
                    Classification.WORKER_RUNTIME),
            Map.entry(
                    "settings.gradle.kts|includedProject=sandbox-worker bootJarTask=implicit "
                            + "build=sandbox-worker/build.gradle.kts",
                    Classification.SANDBOX_RUNTIME),
            Map.entry(
                    "platform-distribution/build.gradle.kts|"
                            + "mainClass.set(\"com.example.platform.distribution.PlatformDistributionLauncher\")",
                    Classification.OTHER_EXPLICIT),
            Map.entry(
                    "platform-app/src/main/java/com/example/platform/PlatformApplication.java|"
                            + "com.example.platform.PlatformApplication",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            Map.entry(
                    "platform-distribution/src/main/java/com/example/platform/distribution/"
                            + "PlatformDistributionLauncher.java|"
                            + "com.example.platform.distribution.PlatformDistributionLauncher",
                    Classification.OTHER_EXPLICIT),
            Map.entry(
                    "remote-render-worker/src/main/java/com/example/platform/remoterender/"
                            + "RemoteRenderWorkerApplication.java|"
                            + "com.example.platform.remoterender.RemoteRenderWorkerApplication",
                    Classification.WORKER_RUNTIME),
            Map.entry(
                    "sandbox-worker/src/main/java/com/example/platform/sandbox/worker/"
                            + "SandboxWorkerApplication.java|"
                            + "com.example.platform.sandbox.worker.SandboxWorkerApplication",
                    Classification.SANDBOX_RUNTIME),
            Map.entry(
                    "platform-app/build/libs/platform-app.jar|"
                            + "Start-Class=com.example.platform.PlatformApplication Main-Class="
                            + SPRING_BOOT_JAR_LAUNCHER,
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            Map.entry(
                    "platform-distribution/build/libs/media-platform-all-in-one.jar|"
                            + "Start-Class=com.example.platform.distribution.PlatformDistributionLauncher "
                            + "Main-Class=" + SPRING_BOOT_JAR_LAUNCHER,
                    Classification.OTHER_EXPLICIT),
            Map.entry(
                    "remote-render-worker/build/libs/remote-render-worker.jar|"
                            + "Start-Class=com.example.platform.remoterender.RemoteRenderWorkerApplication "
                            + "Main-Class=" + SPRING_BOOT_JAR_LAUNCHER,
                    Classification.WORKER_RUNTIME),
            Map.entry(
                    "sandbox-worker/build/libs/sandbox-worker-0.0.1-SNAPSHOT.jar|"
                            + "Start-Class=com.example.platform.sandbox.worker.SandboxWorkerApplication "
                            + "Main-Class=" + SPRING_BOOT_JAR_LAUNCHER,
                    Classification.SANDBOX_RUNTIME));
    private static final Map<String, Classification> KNOWN_DOCKER_LAUNCHES = Map.ofEntries(
            docker("Dockerfile", "ENTRYPOINT [\"sh\", \"-c\", \"exec java $JAVA_OPTS -jar /app/app.jar\"]",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            docker("Dockerfile.ffmpeg-worker",
                    "ENTRYPOINT [\"java\", \"-jar\", \"/app/platform-app.jar\", "
                            + "\"--spring.profiles.active=ffmpeg-worker\"]",
                    Classification.WORKER_RUNTIME),
            docker("Dockerfile.optimized", "ENTRYPOINT [\"sh\", \"-c\", "
                            + "\"exec java $JAVA_OPTS -jar /app/app.jar\"]",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            docker("Dockerfile.simple", "ENTRYPOINT [\"sh\", \"-c\", "
                            + "\"exec java $JAVA_OPTS -jar /app/app.jar\"]",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            docker("frontend/Dockerfile",
                    "CMD [\"npm\", \"run\", \"dev\", \"--\", \"--host\", \"0.0.0.0\"]",
                    Classification.DISABLED_NONPRODUCTION),
            docker("infra/docker/Dockerfile.backend", "ENTRYPOINT [\"sh\", \"-c\", "
                            + "\"exec java $JAVA_OPTS -jar /app/app.jar\"]",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            docker("infra/docker/Dockerfile.render-worker-javacv",
                    "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]",
                    Classification.WORKER_RUNTIME),
            docker("infra/docker/Dockerfile.render-worker-ofx",
                    "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]",
                    Classification.WORKER_RUNTIME),
            docker("remote-render-worker/Dockerfile", "ENTRYPOINT [\"sh\", \"-c\", "
                            + "\"exec java $JAVA_OPTS -jar /app/app.jar\"]",
                    Classification.WORKER_RUNTIME),
            docker("sandbox-worker/Dockerfile",
                    "CMD python3 -c \"import urllib.request; "
                            + "urllib.request.urlopen('http://localhost:8091/v1/sandbox/healthz')\" || exit 1",
                    Classification.SANDBOX_RUNTIME),
            docker("sandbox-worker/Dockerfile",
                    "ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]",
                    Classification.SANDBOX_RUNTIME));
    private static final Map<String, Classification> KNOWN_DEPLOYMENT_CONTAINERS = Map.ofEntries(
            deployment("gitops/production/deployment-api.yaml", "image: ghcr.io/example/platform-api:v1.0.0",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            deployment("gitops/production/deployment-egress-proxy.yaml",
                    "image: ghcr.io/example/egress-proxy:v1.0.0", Classification.OTHER_EXPLICIT),
            deployment("gitops/production/deployment-render-worker.yaml",
                    "image: ghcr.io/example/platform-render-worker:v1.0.0", Classification.WORKER_RUNTIME),
            deployment("gitops/production/deployment-sandbox-worker.yaml",
                    "image: ghcr.io/example/platform-sandbox-worker:v1.0.0", Classification.SANDBOX_RUNTIME),
            deployment("gitops/staging/deployment-api.yaml", "image: ghcr.io/example/platform-api:v1.0.0",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            deployment("gitops/staging/deployment-egress-proxy.yaml",
                    "image: ghcr.io/example/egress-proxy:v1.0.0", Classification.OTHER_EXPLICIT),
            deployment("gitops/staging/deployment-render-worker.yaml",
                    "image: ghcr.io/example/platform-render-worker:v1.0.0", Classification.WORKER_RUNTIME),
            deployment("gitops/staging/deployment-sandbox-worker.yaml",
                    "image: ghcr.io/example/platform-sandbox-worker:v1.0.0", Classification.SANDBOX_RUNTIME),
            deployment("k8s/base/deployment-api.yaml", "image: platform-api:dev",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            deployment("k8s/base/deployment-egress-proxy.yaml", "image: ubuntu/squid:6.6-22.04_stable",
                    Classification.OTHER_EXPLICIT),
            deployment("k8s/base/deployment-render-worker.yaml", "image: platform-render-worker:dev",
                    Classification.WORKER_RUNTIME),
            deployment("k8s/base/deployment-sandbox-worker.yaml", "image: media-platform/sandbox-worker:dev",
                    Classification.SANDBOX_RUNTIME),
            deployment("k8s/deployment-api.yaml", "image: platform-api:dev",
                    Classification.CANONICAL_APPLICATION_RUNTIME),
            deployment("k8s/deployment-render-worker.yaml", "image: platform-render-worker:dev",
                    Classification.WORKER_RUNTIME),
            deployment("k8s/deployment-sandbox-worker.yaml", "image: media-platform/sandbox-worker:dev",
                    Classification.SANDBOX_RUNTIME));

    InventoryReport inspect(Path repositoryRoot) throws IOException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("settings.gradle.kts"))
                && !Files.isRegularFile(root.resolve("settings.gradle"))) {
            throw new IllegalArgumentException("not a Gradle repository root: " + root);
        }

        List<ExecutableFact> facts = new ArrayList<>();
        EnumSet<ScanArea> inspected = EnumSet.noneOf(ScanArea.class);
        discoverIncludedBootProjects(root, facts, inspected);
        discoverBuildLaunchDeclarations(root, facts, inspected);
        discoverProductionMainClasses(root, facts, inspected);
        discoverDockerLaunches(root, facts, inspected);
        discoverComposeLaunches(root, facts, inspected);
        discoverDeploymentContainers(root, facts, inspected);
        discoverLaunchScripts(root, facts, inspected);
        discoverBuiltExecutableArtifacts(root, facts, inspected);
        return classify(facts, inspected);
    }

    InventoryReport classify(List<ExecutableFact> facts) {
        return classify(facts, EnumSet.allOf(ScanArea.class));
    }

    private InventoryReport classify(List<ExecutableFact> facts, Set<ScanArea> inspectedAreas) {
        List<ExecutableFact> discovered = List.copyOf(new LinkedHashSet<>(facts));
        List<ClassifiedExecutable> classified = new ArrayList<>();
        List<ExecutableFact> unclassified = new ArrayList<>();
        for (ExecutableFact fact : discovered) {
            Optional<Classification> classification = classificationOf(fact);
            if (classification.isPresent()) {
                classified.add(new ClassifiedExecutable(fact, classification.orElseThrow()));
            } else {
                unclassified.add(fact);
            }
        }
        return new InventoryReport(
                discovered, List.copyOf(classified), List.copyOf(unclassified),
                Set.copyOf(inspectedAreas));
    }

    private Optional<Classification> classificationOf(ExecutableFact fact) {
        String text = fact.searchableText();
        return switch (fact.kind()) {
            case INCLUDED_BOOT_JAR_PROJECT, BUILD_DECLARED_LAUNCH,
                    PRODUCTION_MAIN_CLASS, BUILT_EXECUTABLE_ARTIFACT ->
                    classifyJavaDistribution(fact);
            case DOCKER_LAUNCH -> Optional.ofNullable(
                    KNOWN_DOCKER_LAUNCHES.get(identity(fact)));
            case COMPOSE_SERVICE_LAUNCH -> classifyCompose(text);
            case DEPLOYMENT_CONTAINER -> Optional.ofNullable(
                    KNOWN_DEPLOYMENT_CONTAINERS.get(identity(fact)));
            case LAUNCH_SCRIPT -> classifyScript(fact);
        };
    }

    private Optional<Classification> classifyJavaDistribution(ExecutableFact fact) {
        return Optional.ofNullable(KNOWN_JAVA_DISTRIBUTIONS.get(identity(fact)));
    }

    private Optional<Classification> classifyCompose(String text) {
        if (text.contains("docs/examples/") || text.contains("infra/lab/")
                || text.contains("infra/natron/") || text.contains("docker-compose.dev")
                || text.contains("docker-compose.local") || text.contains("docker-compose.authentik")) {
            return Optional.of(Classification.DISABLED_NONPRODUCTION);
        }
        if (text.contains("sandbox")) {
            return Optional.of(Classification.SANDBOX_RUNTIME);
        }
        if (text.contains("render-worker") || text.contains("remote-render")) {
            return Optional.of(Classification.WORKER_RUNTIME);
        }
        if (text.contains("service=frontend")) {
            return Optional.of(Classification.OTHER_EXPLICIT);
        }
        if (text.contains(" service=app ") || text.contains(" service=backend ")
                || text.contains("service=platform") || text.contains("dockerfile=dockerfile")) {
            return Optional.of(Classification.CANONICAL_APPLICATION_RUNTIME);
        }
        if (text.contains("postgres") || text.contains("redis") || text.contains("authentik")
                || text.contains("rustfs") || text.contains("opencue") || text.contains("cuebot")
                || text.contains("rqd")) {
            return Optional.of(Classification.OTHER_EXPLICIT);
        }
        return Optional.empty();
    }

    private Optional<Classification> classifyScript(ExecutableFact fact) {
        String source = sourcePath(fact.source());
        if (source.contains("/src/test/") || source.startsWith("test-assets/")) {
            return Optional.of(Classification.TEST_ONLY_EXECUTABLE);
        }
        if (source.startsWith("docs/examples/")
                || source.equals("render-module/src/main/resources/natron/poc-render.sh")) {
            return Optional.of(Classification.DISABLED_NONPRODUCTION);
        }
        if (source.equals("gradlew")
                || source.startsWith("docs/architecture/governance/automated-guards/")
                || source.startsWith("docs/architecture/maps/scripts/")
                || source.startsWith("frontend/scripts/")
                || source.startsWith("infra/scripts/")
                || source.startsWith("scripts/")
                || source.equals("typed-schema-module/regenerate-jooq-schema.sh")) {
            return Optional.of(Classification.BUILD_ONLY_TOOL);
        }
        return Optional.empty();
    }

    private void discoverIncludedBootProjects(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.SETTINGS_AND_INCLUDED_PROJECTS);
        Path settings = Files.isRegularFile(root.resolve("settings.gradle.kts"))
                ? root.resolve("settings.gradle.kts") : root.resolve("settings.gradle");
        Matcher projects = QUOTED_PROJECT.matcher(Files.readString(settings));
        while (projects.find()) {
            String declared = projects.group(1);
            Path project = root.resolve(declared.replaceFirst("^:", "").replace(':', '/'));
            Path kotlinBuild = project.resolve("build.gradle.kts");
            Path groovyBuild = project.resolve("build.gradle");
            Path build = Files.isRegularFile(kotlinBuild) ? kotlinBuild : groovyBuild;
            if (Files.isRegularFile(build) && appliesSpringBootPlugin(build)) {
                facts.add(new ExecutableFact(
                        FactKind.INCLUDED_BOOT_JAR_PROJECT, relative(root, settings),
                        "includedProject=" + declared + " bootJarTask=implicit build="
                                + relative(root, build)));
            }
        }
    }

    private boolean appliesSpringBootPlugin(Path build) throws IOException {
        return Files.readAllLines(build).stream()
                .map(String::trim)
                .anyMatch(line -> (line.contains("id(\"org.springframework.boot\")")
                        || line.contains("id 'org.springframework.boot'"))
                        && !line.contains("apply false"));
    }

    private void discoverBuildLaunchDeclarations(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.GRADLE_BOOT_JAR_APPLICATION_AND_JAVA_EXEC);
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path build : paths.filter(this::isBuildFile).filter(this::isRepositorySource).toList()) {
                List<String> lines = Files.readAllLines(build);
                for (int index = 0; index < lines.size(); index++) {
                    String compact = lines.get(index).trim();
                    if (compact.contains("mainClass") || compact.contains("JavaExec")
                            || compact.matches(".*id\\([\\\"']application[\\\"']\\).*")
                            || compact.startsWith("application {")) {
                        facts.add(new ExecutableFact(
                                FactKind.BUILD_DECLARED_LAUNCH,
                                relative(root, build) + ":" + (index + 1), compact));
                    }
                }
            }
        }
    }

    private void discoverProductionMainClasses(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.PRODUCTION_MAIN_CLASSES);
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path source : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/"))
                    .filter(this::isRepositorySource).toList()) {
                String text = Files.readString(source);
                if (!JAVA_MAIN.matcher(text).find()) {
                    continue;
                }
                Matcher packageMatcher = JAVA_PACKAGE.matcher(text);
                Matcher typeMatcher = JAVA_TYPE.matcher(text);
                String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
                String typeName = typeMatcher.find() ? typeMatcher.group(1) : source.getFileName().toString();
                facts.add(new ExecutableFact(
                        FactKind.PRODUCTION_MAIN_CLASS, relative(root, source),
                        packageName.isBlank() ? typeName : packageName + "." + typeName));
            }
        }
    }

    private void discoverDockerLaunches(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.DOCKERFILES);
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path dockerfile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("Dockerfile"))
                    .filter(this::isRepositorySource).toList()) {
                addDirectiveFacts(root, dockerfile, facts, FactKind.DOCKER_LAUNCH, "ENTRYPOINT", "CMD");
            }
        }
    }

    private void discoverComposeLaunches(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.COMPOSE_FILES);
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path compose : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("compose"))
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .filter(this::isRepositorySource).toList()) {
                String service = "unknown";
                List<String> lines = Files.readAllLines(compose);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (line.matches("^  [A-Za-z0-9_.-]+:\\s*$")) {
                        service = line.trim().replaceFirst(":$", "");
                    }
                    String compact = line.trim();
                    if (compact.startsWith("image:") || compact.startsWith("command:")
                            || compact.startsWith("dockerfile:")) {
                        facts.add(new ExecutableFact(
                                FactKind.COMPOSE_SERVICE_LAUNCH,
                                relative(root, compose) + ":" + (index + 1),
                                "service=" + service + " " + compact.replace(':', '=')));
                    }
                }
            }
        }
    }

    private void discoverDeploymentContainers(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.DEPLOYMENT_MANIFESTS);
        for (String deploymentRoot : List.of("gitops", "k8s")) {
            Path directory = root.resolve(deploymentRoot);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path manifest : paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".yaml")
                                || path.toString().endsWith(".yml"))
                        .toList()) {
                    addDirectiveFacts(root, manifest, facts, FactKind.DEPLOYMENT_CONTAINER, "image:");
                }
            }
        }
    }

    private void discoverLaunchScripts(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.WORKER_SANDBOX_AND_SUPPORT_SCRIPTS);
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path script : paths.filter(Files::isRegularFile)
                    .filter(this::isRepositorySource).toList()) {
                String firstLine = readShebang(script);
                if (Files.isExecutable(script) || firstLine.startsWith("#!")) {
                    facts.add(new ExecutableFact(
                            FactKind.LAUNCH_SCRIPT, relative(root, script),
                            firstLine.isBlank() ? "executable-bit" : firstLine));
                }
            }
        }
    }

    private void discoverBuiltExecutableArtifacts(
            Path root, List<ExecutableFact> facts, Set<ScanArea> inspected) throws IOException {
        inspected.add(ScanArea.BUILT_EXECUTABLE_ARTIFACTS);
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path jarPath : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/build/libs/"))
                    .filter(path -> path.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    if (jar.getManifest() == null) {
                        continue;
                    }
                    Attributes attributes = jar.getManifest().getMainAttributes();
                    String startClass = attributes.getValue("Start-Class");
                    String mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
                    if (startClass != null || mainClass != null) {
                        facts.add(new ExecutableFact(
                                FactKind.BUILT_EXECUTABLE_ARTIFACT, relative(root, jarPath),
                                "Start-Class=" + startClass + " Main-Class=" + mainClass));
                    }
                }
            }
        }
    }

    private void addDirectiveFacts(
            Path root, Path source, List<ExecutableFact> facts, FactKind kind,
            String... directives) throws IOException {
        List<String> lines = Files.readAllLines(source);
        for (int index = 0; index < lines.size(); index++) {
            String compact = lines.get(index).trim();
            for (String directive : directives) {
                if (compact.startsWith(directive)) {
                    facts.add(new ExecutableFact(
                            kind, relative(root, source) + ":" + (index + 1), compact));
                    break;
                }
            }
        }
    }

    private boolean isBuildFile(Path path) {
        String name = path.getFileName().toString();
        return Files.isRegularFile(path) && (name.equals("build.gradle") || name.equals("build.gradle.kts"));
    }

    private boolean isRepositorySource(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return !normalized.contains("/.git/") && !normalized.contains("/.gradle/")
                && !normalized.contains("/build/") && !normalized.contains("/node_modules/")
                && !normalized.contains("/.agent-tasks/") && !normalized.contains("/.agents/")
                && !normalized.contains("/.codex/");
    }

    private static String readShebang(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            int first = input.read();
            int second = input.read();
            if (first != '#' || second != '!') {
                return "";
            }
            StringBuilder line = new StringBuilder("#!");
            for (int next = input.read(); next >= 0 && next != '\n' && line.length() < 4096;
                    next = input.read()) {
                if (next != '\r') {
                    line.append((char) next);
                }
            }
            return line.toString();
        }
    }

    private static Map.Entry<String, Classification> docker(
            String source, String detail, Classification classification) {
        return Map.entry(source + "|" + detail, classification);
    }

    private static Map.Entry<String, Classification> deployment(
            String source, String detail, Classification classification) {
        return Map.entry(source + "|" + detail, classification);
    }

    private static String identity(ExecutableFact fact) {
        return sourcePath(fact.source()) + "|" + fact.detail();
    }

    private static String sourcePath(String source) {
        return source.replaceFirst(":\\d+$", "");
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
