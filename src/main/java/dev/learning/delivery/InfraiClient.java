package dev.learning.delivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class InfraiClient implements DomainGateway {
    private static final int MAX_ATTEMPTS = 4;
    private final HttpClient http;
    private final DeliveryConfig config;

    public InfraiClient(DeliveryConfig config) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), config);
    }

    InfraiClient(HttpClient http, DeliveryConfig config) {
        this.http = http;
        this.config = config;
    }

    @Override
    public Map<String, Object> addZone(String domain, String operationId) {
        return request("POST", "/v1/dns/domain/add", Map.of("domain", domain), operationId);
    }

    @Override
    public Map<String, Object> upsertRecord(String zoneId, String type, String name, String content, String operationId) {
        return request("PUT", "/v1/dns/record/upsert", Map.of(
                "zone_id", zoneId, "record_type", type, "name", name, "content", content, "ttl", 300), operationId);
    }

    @Override
    public Map<String, Object> verifyDns(String domain, String operationId) {
        return request("POST", "/v1/dns/domain/verify", Map.of("domain", domain), operationId);
    }

    @Override
    public Map<String, Object> verifySendingDomain(String domain, String operationId) {
        return request("POST", "/v1/email/domain/verify", Map.of("domain", domain), operationId);
    }

    @Override
    public Map<String, Object> rotateDkim(String domain, String operationId) {
        return request("POST", "/v1/email/domain/rotate_dkim/" + domain, Map.of(), operationId);
    }

    private Map<String, Object> request(String method, String path, Map<String, Object> body, String operationId) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", operationId)
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Map<String, Object> envelope = Json.object(response.body());
                if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                    sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    throw InfraiException.from(response.statusCode(), envelope.get("error"));
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Infrai transport response " + response.statusCode());
                }
                return objectValue(envelope.get("data"), "data");
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }
        }
        throw new IllegalStateException("Retry attempts exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Long.parseLong(value) * 1000L; }
                    catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
                })
                .orElse(250L * (1L << attempt));
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(Math.min(millis, 10_000L));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> objectValue(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalStateException("Expected object in " + field);
        return (Map<String, Object>) value;
    }

    public static final class InfraiException extends RuntimeException {
        private final int httpStatus;
        private final String code;

        private InfraiException(int httpStatus, String code, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }

        static InfraiException from(int status, Object errorValue) {
            Map<String, Object> error = objectValue(errorValue, "error");
            String code = String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED"));
            String message = String.valueOf(error.getOrDefault("message", error));
            return new InfraiException(status, code, message);
        }

        public int httpStatus() { return httpStatus; }
        public String code() { return code; }
    }
}
