package com.intellicampus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Automatically loads .env configuration from the project root or current working directory
 * into Java System properties before Spring Boot application startup.
 * Never prints or leaks secret values.
 */
public class DotEnvLoader {

    private static final Logger logger = LoggerFactory.getLogger(DotEnvLoader.class);

    public static void load() {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] searchLocations = new Path[] {
                userDir.resolve(".env"),
                userDir.resolve("..").resolve(".env"),
                userDir.resolve("../..").resolve(".env"),
                Paths.get(".env"),
                Paths.get("..", ".env"),
                Paths.get("../..", ".env")
        };

        Path envFile = null;
        for (Path candidate : searchLocations) {
            if (Files.isRegularFile(candidate)) {
                envFile = candidate.toAbsolutePath().normalize();
                break;
            }
        }

        if (envFile == null) {
            logger.info("[DOTENV] No .env file found in search paths; continuing with environment/defaults.");
            return;
        }

        logger.info("[DOTENV] Loading environment configuration from {}", envFile);

        try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            String line;
            int loadedCount = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();

                // Strip outer quotes if present
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    if (value.length() >= 2) {
                        value = value.substring(1, value.length() - 1).trim();
                    }
                }

                // Never override actual OS environment variables, but populate System properties
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    loadedCount++;
                }

                // Map MAIL_* (and legacy SMTP_* as fallback) to Spring Boot spring.mail.* and MAIL_* properties
                if ("MAIL_HOST".equals(key) || "SMTP_HOST".equals(key)) {
                    if (System.getProperty("spring.mail.host") == null) System.setProperty("spring.mail.host", value);
                    if (System.getProperty("MAIL_HOST") == null) System.setProperty("MAIL_HOST", value);
                } else if ("MAIL_PORT".equals(key) || "SMTP_PORT".equals(key)) {
                    if (System.getProperty("spring.mail.port") == null) System.setProperty("spring.mail.port", value);
                    if (System.getProperty("MAIL_PORT") == null) System.setProperty("MAIL_PORT", value);
                } else if ("MAIL_USERNAME".equals(key) || "SMTP_USERNAME".equals(key)) {
                    if (System.getProperty("spring.mail.username") == null) System.setProperty("spring.mail.username", value);
                    if (System.getProperty("MAIL_USERNAME") == null) System.setProperty("MAIL_USERNAME", value);
                } else if ("MAIL_PASSWORD".equals(key) || "SMTP_PASSWORD".equals(key)) {
                    if (System.getProperty("spring.mail.password") == null) System.setProperty("spring.mail.password", value);
                    if (System.getProperty("MAIL_PASSWORD") == null) System.setProperty("MAIL_PASSWORD", value);
                } else if ("MAIL_FROM".equals(key) || "SMTP_FROM".equals(key)) {
                    if (System.getProperty("app.mail-from") == null) System.setProperty("app.mail-from", value);
                    if (System.getProperty("MAIL_FROM") == null) System.setProperty("MAIL_FROM", value);
                }
            }

            logger.info("[DOTENV] Successfully loaded {} properties from .env", loadedCount);
        } catch (Exception e) {
            logger.warn("[DOTENV] Failed reading .env file from {}: {}", envFile, e.getMessage());
        }
    }
}
