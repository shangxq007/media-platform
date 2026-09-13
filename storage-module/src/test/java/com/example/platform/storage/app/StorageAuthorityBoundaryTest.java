package com.example.platform.storage.app;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class StorageAuthorityBoundaryTest {
    static List<String> violations(Path root) throws Exception {
        var result=new ArrayList<String>();
        try(var files=Files.walk(root)){
            for(var p:files.filter(f->f.toString().endsWith(".java")).toList()){
                String s=Files.readString(p);
                if(s.contains("import com.example.platform.storage.infrastructure.")
                    || s.matches("(?s).*\\b(?:insertInto|update|deleteFrom)\\s*\\(\\s*STORAGE_REFERENCE\\b.*")
                    || p.getFileName().toString().equals("StorageReferenceRepository.java")
                    || p.getFileName().toString().equals("S3StorageProvider.java")
                    || p.getFileName().toString().equals("StorageRuntimeService.java"))result.add(p.toString());
            }
        }return result;
    }
    @Test void renderConsumesStorageOwnerContracts() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();
        assertEquals(List.of(),violations(root.resolve("render-module/src/main/java")));
    }
    @Test void detectsForeignWriterAndInfrastructureReintroduction(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("Caller.java"),"import com.example.platform.storage.infrastructure.S3ObjectWriter;");
        Files.writeString(root.resolve("Writer.java"),"dsl.insertInto(STORAGE_REFERENCE).execute();");
        Files.writeString(root.resolve("StorageReferenceRepository.java"),"class StorageReferenceRepository {}");
        assertEquals(3,violations(root).size());
    }
}
