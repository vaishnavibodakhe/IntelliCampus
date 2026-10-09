package com.intellicampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelliCampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelliCampusApplication.class, args);
    }
}
