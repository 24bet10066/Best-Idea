package com.partlinq.core.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Production DataSource configuration.
 *
 * Handles both URL formats Render can give you:
 *
 * Format A — raw Render URL (what Render shows on the database page):
 *   postgresql://user:password@host:5432/dbname
 *
 * Format B — JDBC URL with separate credentials (what our docs say to use):
 *   jdbc:postgresql://host:5432/dbname  (+ DATABASE_USER + DATABASE_PASSWORD)
 *
 * If DATABASE_URL starts with "postgresql://" (no "jdbc:" prefix), this config
 * extracts user/password/host/port/database from it automatically.
 * DATABASE_USER and DATABASE_PASSWORD env vars override anything in the URL.
 */
@Configuration
@Profile("prod")
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${DATABASE_URL}")
    private String rawDatabaseUrl;

    @Value("${DATABASE_USER:}")
    private String databaseUser;

    @Value("${DATABASE_PASSWORD:}")
    private String databasePassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // Parse the URL regardless of format
        ParsedDbUrl parsed = parseUrl(rawDatabaseUrl);

        config.setJdbcUrl(parsed.jdbcUrl);
        config.setUsername(databaseUser.isEmpty() ? parsed.user : databaseUser);
        config.setPassword(databasePassword.isEmpty() ? parsed.password : databasePassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Pool sizing for Render Free (512MB)
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);

        // Connection health check
        config.setConnectionTestQuery("SELECT 1");

        log.info("DataSource configured: {}", parsed.jdbcUrl);
        return new HikariDataSource(config);
    }

    private ParsedDbUrl parseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("DATABASE_URL env var is not set");
        }

        // Already a proper JDBC URL — use as-is
        if (url.startsWith("jdbc:postgresql://")) {
            return new ParsedDbUrl(url, "", "");
        }

        // Raw Render/Heroku format: postgresql://user:pass@host:port/dbname
        if (url.startsWith("postgresql://")) {
            try {
                URI uri = new URI(url.replace("postgresql://", "http://"));
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath(); // "/dbname"
                String userInfo = uri.getUserInfo(); // "user:password"

                String user = "";
                String password = "";
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    user = parts[0];
                    password = parts[1];
                }

                // Add .oregon-postgres.internal suffix if host looks like a Render internal host
                // and doesn't already have a domain suffix
                if (!host.contains(".") && host.startsWith("dpg-")) {
                    host = host + ".oregon-postgres.internal";
                    log.info("Auto-appended .oregon-postgres.internal to host: {}", host);
                }

                String jdbcUrl = String.format("jdbc:postgresql://%s:%d%s", host, port, path);
                return new ParsedDbUrl(jdbcUrl, user, password);

            } catch (Exception e) {
                throw new IllegalStateException(
                    "Could not parse DATABASE_URL: " + url + "\n" +
                    "Expected format: postgresql://user:password@host:5432/dbname\n" +
                    "Or JDBC format:  jdbc:postgresql://host:5432/dbname", e);
            }
        }

        throw new IllegalStateException(
            "DATABASE_URL must start with 'jdbc:postgresql://' or 'postgresql://'.\n" +
            "Got: " + url.substring(0, Math.min(50, url.length())) + "..."
        );
    }

    private record ParsedDbUrl(String jdbcUrl, String user, String password) {}
}
