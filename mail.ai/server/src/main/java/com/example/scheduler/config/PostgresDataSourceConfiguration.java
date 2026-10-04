package com.example.scheduler.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Configuration
@Profile("postgres")
public class PostgresDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties, Environment environment) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        String caCertificatePath = environment.getProperty("DB_SSL_ROOT_CERT");
        if (!StringUtils.hasText(caCertificatePath)) {
            return dataSource;
        }

        Path certificate = Path.of(caCertificatePath.trim());
        if (!certificate.isAbsolute() || !Files.isRegularFile(certificate) || !Files.isReadable(certificate)) {
            throw new IllegalStateException("DB_SSL_ROOT_CERT must be an absolute path to a readable CA certificate file.");
        }

        String jdbcUrl = properties.determineUrl();
        String sslMode = Arrays.stream(jdbcUrl.split("\\?", 2))
                .skip(1)
                .flatMap(query -> Arrays.stream(query.split("&")))
                .filter(parameter -> parameter.startsWith("sslmode="))
                .map(parameter -> parameter.substring("sslmode=".length()))
                .findFirst()
                .orElse("");
        if (!"verify-full".equalsIgnoreCase(sslMode)) {
            throw new IllegalStateException("When DB_SSL_ROOT_CERT is set, DB_URL must use sslmode=verify-full.");
        }

        dataSource.addDataSourceProperty("sslrootcert", certificate.toString());
        return dataSource;
    }
}
