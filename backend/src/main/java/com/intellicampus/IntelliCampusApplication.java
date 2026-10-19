package com.intellicampus;

import com.intellicampus.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelliCampusApplication {

    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(IntelliCampusApplication.class, args);
    }
}
