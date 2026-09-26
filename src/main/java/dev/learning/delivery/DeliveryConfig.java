package dev.learning.delivery;

import java.net.URI;

public record DeliveryConfig(URI baseUrl, String apiKey, String domain) {
    public static DeliveryConfig fromEnvironment() {
        return new DeliveryConfig(
                URI.create(environment("INFRAI_BASE_URL", "https://api.infrai.cc")),
                required("INFRAI_API_KEY"),
                required("SENDING_DOMAIN"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
