package com.example.platform.remoterender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Thin runtime host; canonical persistence and lifecycle stay in the platform. */
@SpringBootApplication(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class RemoteRenderWorkerApplication {
    public static void main(String[] args) { SpringApplication.run(RemoteRenderWorkerApplication.class, args); }
}
