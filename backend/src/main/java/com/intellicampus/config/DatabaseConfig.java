package com.intellicampus.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env, DataSourceProperties defaultProperties) {
        String dbUrl = env.getProperty("DATABASE_URL");

        HikariConfig config = new HikariConfig();
        String driverClass = defaultProperties.determineDriverClassName();
        if (driverClass != null && !driverClass.trim().isEmpty()) {
            config.setDriverClassName(driverClass.trim());
        } else {
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        if (dbUrl != null && !dbUrl.trim().isEmpty() && !dbUrl.contains("USER:PASSWORD") && !dbUrl.contains("your_password")) {
            try {
                String cleanUrl = dbUrl.trim();
                if (cleanUrl.startsWith("jdbc:mysql://")) {
                    config.setJdbcUrl(cleanUrl);
                    if (env.containsProperty("DB_USER")) config.setUsername(env.getProperty("DB_USER"));
                    if (env.containsProperty("DB_PASSWORD")) config.setPassword(env.getProperty("DB_PASSWORD"));
                    log.info("[DATABASE_URL] Configured HikariDataSource directly from jdbc:mysql URL");
                } else if (cleanUrl.startsWith("mysql://")) {
                    URI uri = new URI(cleanUrl.replace("mysql://", "http://"));
                    String userInfo = uri.getUserInfo();
                    String user = null;
                    String pass = null;
                    if (userInfo != null && userInfo.contains(":")) {
                        int colonIdx = userInfo.indexOf(':');
                        user = userInfo.substring(0, colonIdx);
                        pass = userInfo.substring(colonIdx + 1);
                    }
                    int port = uri.getPort() > 0 ? uri.getPort() : 3306;
                    String host = uri.getHost();
                    String path = uri.getPath();
                    String dbName = (path != null && path.length() > 1) ? path.substring(1) : "intellicampus";

                    String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&characterEncoding=UTF-8",
                            host, port, dbName);

                    config.setJdbcUrl(jdbcUrl);
                    if (user != null) config.setUsername(user);
                    if (pass != null) config.setPassword(pass);
                    log.info("[DATABASE_URL] Configured HikariDataSource from DATABASE_URL for host: {}, db: {}", host, dbName);
                }
            } catch (Exception e) {
                log.warn("[DATABASE_URL] Failed to parse DATABASE_URL ({}), falling back to standard datasource properties", e.getMessage());
            }
        }

        if (config.getJdbcUrl() == null) {
            config.setJdbcUrl(defaultProperties.determineUrl());
            config.setUsername(defaultProperties.determineUsername());
            config.setPassword(defaultProperties.determinePassword());
            log.info("[DATASOURCE] Configured HikariDataSource from standard Spring properties");
        }

        return new HikariDataSource(config);
    }
}
