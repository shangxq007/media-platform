package com.example.platform.remoterender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.example.platform.remoterender",
        "com.example.platform.render.infrastructure",
        "com.example.platform.storage"
})
@EnableScheduling
public class RemoteRenderWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RemoteRenderWorkerApplication.class, args);
    }
}
