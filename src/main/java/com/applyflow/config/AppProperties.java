package com.applyflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("dev") String version,
        @DefaultValue("UTC") String timezone,
        Security security,
        Encryption encryption,
        Seed seed,
        Sync sync,
        Cors cors,
        Intelligence intelligence) {

    public static final String DEV_PASSWORD = "applyflow";
    public static final String DEV_SESSION_SECRET = "dev-session-secret-change-me";
    public static final String DEV_ENCRYPTION_KEY = "dev-only-encryption-key-change-me";

    public record Security(@DefaultValue("admin") String username,
                           @DefaultValue(DEV_PASSWORD) String password,
                           @DefaultValue("Abhinay") String displayName,
                           @DefaultValue(DEV_SESSION_SECRET) String sessionSecret) {
        @Override
        public String toString() {
            return "Security{username=" + username + "}";
        }
    }

    public record Encryption(@DefaultValue(DEV_ENCRYPTION_KEY) String key) {
        @Override
        public String toString() {
            return "Encryption{***}";
        }
    }

    public record Seed(@DefaultValue("true") boolean enabled) {
    }

    public record Sync(@DefaultValue("true") boolean enabled,
                       @DefaultValue("50") int batchSize,
                       @DefaultValue("204800") int maxBodyBytes,
                       @DefaultValue("3") int executorThreads) {
    }

    public record Cors(@DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins) {
    }

    public record Intelligence(@DefaultValue("rules") String provider) {
    }
}
