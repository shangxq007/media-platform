package com.example.platform.delivery.app;
/** Applies the exact production V1; no miniature replacement tables. */
final class DeliveryTestSchema {
 static void migrate(String url,String user,String password,String schema){
    java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();
    while(!java.nio.file.Files.exists(root.resolve("platform-app/src/main/resources/db/migration")))root=root.getParent();
    org.flywaydb.core.Flyway.configure().dataSource(url,user,password)
        .locations("filesystem:"+root.resolve("platform-app/src/main/resources/db/migration"))
        .schemas(schema).defaultSchema(schema).load().migrate();
 }
}
