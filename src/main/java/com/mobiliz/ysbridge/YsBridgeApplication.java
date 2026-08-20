package com.mobiliz.ysbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the YS Bridge translation middleware.
 *
 * <p>The bridge exposes the legacy "Yetkili Servis" API under a clean, English, resource-oriented
 * contract. Field and value translation is driven entirely by YAML mapping definitions, so new
 * endpoints are added without code changes.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class YsBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(YsBridgeApplication.class, args);
    }
}
