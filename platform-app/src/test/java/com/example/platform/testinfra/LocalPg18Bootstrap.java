package com.example.platform.testinfra;

import com.example.platform.shared.test.TestDatabaseConnectionProvider;
import org.flywaydb.core.Flyway;

/** Real local-PG18 preflight. Consumes the same bound Provider as all business tests. */
public final class LocalPg18Bootstrap {
    public static void migrate() throws Exception {
        var provider=TestDatabaseConnectionProvider.fromControlledLaunch();
        provider.validateConnection();
        var result=Flyway.configure().dataSource(provider.jdbcUrl(),provider.username(),provider.secret())
            .locations("classpath:db/migration").defaultSchema("public").validateMigrationNaming(true).load().migrate();
        System.out.println("REAL_PG18_FLYWAY migrations="+result.migrationsExecuted+" success="+result.success);
        try(var c=provider.openConnection();var s=c.createStatement();var r=s.executeQuery(
            "select current_setting('server_version'),current_database(),current_user,(select ssl from pg_stat_ssl where pid=pg_backend_pid())")) {
            if(!r.next() || !r.getBoolean(4))throw new IllegalStateException("REAL_PG18_TLS_REQUIRED");
            System.out.println("REAL_PG18_IDENTITY version="+r.getString(1)+" db="+r.getString(2)+" role="+r.getString(3)+" tls="+r.getBoolean(4));
        }
    }

    public static void prepare() throws Exception {
        migrate();
        var provider=TestDatabaseConnectionProvider.fromControlledLaunch();
        var root=java.nio.file.Path.of(System.getProperty("project.root.dir"));
        var scratch=java.nio.file.Path.of(System.getProperty("media.platform.pg18.scratch"));
        org.jooq.meta.jaxb.Configuration config;
        try(var input=java.nio.file.Files.newInputStream(root.resolve("typed-schema-module/jooq-codegen.xml"))) {
            config=org.jooq.codegen.GenerationTool.load(input);
        }
        // Keep credentials in the controlled JVM only; never emit substituted XML.
        config.getJdbc().withUrl(provider.jdbcUrl()).withUser(provider.username()).withPassword(provider.secret());
        var output=scratch.resolve("generated-jooq");
        config.getGenerator().getTarget().setDirectory(output.toString());
        org.jooq.codegen.GenerationTool.generate(config);
        var generated=new java.util.ArrayList<String>();
        try(var files=java.nio.file.Files.walk(output)) {
            files.filter(p->p.toString().endsWith(".java")).forEach(p->generated.add(p.toString()));
        }
        if(generated.isEmpty())throw new IllegalStateException("REAL_JOOQ_GENERATION_EMPTY");
        var generatedPackage = output.resolve("com/example/platform/typedschema/jooq/generated");
        var committedPackage = root.resolve("typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated");
        var freshNames = new java.util.TreeSet<String>();
        var committedNames = new java.util.TreeSet<String>();
        try (var files = java.nio.file.Files.walk(generatedPackage)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> freshNames.add(generatedPackage.relativize(p).toString()));
        }
        try (var files = java.nio.file.Files.walk(committedPackage)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> committedNames.add(committedPackage.relativize(p).toString()));
        }
        if (!freshNames.equals(committedNames)) throw new IllegalStateException("REAL_JOOQ_SOURCE_PATH_MISMATCH");
        for (String name : freshNames) {
            if (java.nio.file.Files.mismatch(generatedPackage.resolve(name), committedPackage.resolve(name)) != -1)
                throw new IllegalStateException("REAL_JOOQ_SOURCE_BYTES_MISMATCH: " + name);
        }
        System.out.println("REAL_JOOQ_COMMITTED_PARITY sources=" + freshNames.size() + " exact_bytes=true");
        var classes=scratch.resolve("generated-classes");java.nio.file.Files.createDirectories(classes);
        var options=new java.util.ArrayList<String>(java.util.List.of("-proc:none","-cp",System.getProperty("java.class.path"),"-d",classes.toString()));
        options.addAll(generated);
        int code=javax.tools.ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,options.toArray(String[]::new));
        if(code!=0)throw new IllegalStateException("REAL_JOOQ_GENERATED_COMPILE_FAILED");
        System.out.println("REAL_JOOQ_GENERATED sources="+generated.size()+" compile_exit="+code+" version="+org.jooq.Constants.VERSION);
    }
}
