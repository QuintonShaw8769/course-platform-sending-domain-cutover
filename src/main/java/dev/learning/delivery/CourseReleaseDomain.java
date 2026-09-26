package dev.learning.delivery;

import dev.learning.delivery.SendingDomainOnboarding.DomainPlan;
import dev.learning.delivery.SendingDomainOnboarding.OnboardingReport;

public final class CourseReleaseDomain {
    private CourseReleaseDomain() {}

    public static void main(String[] args) {
        DeliveryConfig config = DeliveryConfig.fromEnvironment();
        SendingDomainOnboarding service = new SendingDomainOnboarding(new InfraiClient(config));
        String command = args.length == 0 ? "onboard" : args[0];
        String releaseId = required("RELEASE_ID");

        if ("rotate-dkim".equals(command)) {
            var event = service.rotateDkim(config.domain(), releaseId);
            System.out.println(Json.write(java.util.Map.of(
                    "type", event.type(), "detail", event.detail(), "observed_at", event.observedAt())));
            return;
        }
        if (!"onboard".equals(command)) throw new IllegalArgumentException("Command must be onboard or rotate-dkim");

        DomainPlan plan = new DomainPlan(
                config.domain(), releaseId, required("SPF_CONTENT"),
                environment("DKIM_RECORD_TYPE", "CNAME"), required("DKIM_NAME"), required("DKIM_CONTENT"),
                required("DMARC_CONTENT"));
        OnboardingReport report = service.onboard(plan);
        var events = report.events().stream().map(event -> java.util.Map.of(
                "type", event.type(), "detail", event.detail(), "observed_at", event.observedAt())).toList();
        System.out.println(Json.write(java.util.Map.of(
                "domain", report.domain(), "zone_id", report.zoneId(), "release_id", report.releaseId(),
                "decision", report.decision().name(), "events", events)));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
