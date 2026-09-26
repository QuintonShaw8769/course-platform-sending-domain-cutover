package dev.learning.delivery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SendingDomainOnboarding {
    private final DomainGateway infrai;

    public SendingDomainOnboarding(DomainGateway infrai) {
        this.infrai = infrai;
    }

    public OnboardingReport onboard(DomainPlan plan) {
        List<BuildEvent> events = new ArrayList<>();
        Map<String, Object> zone = infrai.addZone(plan.domain(), plan.releaseId() + ":zone");
        String zoneId = requiredText(zone, "zone_id");
        events.add(event("ZONE_READY", "DNS zone acquired for release " + plan.releaseId()));

        upsert(zoneId, "TXT", "@", plan.spfContent(), plan.releaseId() + ":spf");
        events.add(event("SPF_PUBLISHED", "SPF TXT record written"));
        upsert(zoneId, plan.dkimType(), plan.dkimName(), plan.dkimContent(), plan.releaseId() + ":dkim");
        events.add(event("DKIM_PUBLISHED", "DKIM record written"));
        upsert(zoneId, "TXT", "_dmarc", plan.dmarcContent(), plan.releaseId() + ":dmarc");
        events.add(event("DMARC_PUBLISHED", "DMARC TXT record written"));

        Map<String, Object> dns = infrai.verifyDns(plan.domain(), plan.releaseId() + ":dns-check");
        Map<String, Object> mail = infrai.verifySendingDomain(plan.domain(), plan.releaseId() + ":mail-check");
        boolean dnsVerified = isVerified(dns);
        boolean mailVerified = isVerified(mail);
        CutoverDecision decision = dnsVerified && mailVerified ? CutoverDecision.CUTOVER : CutoverDecision.HOLD;
        events.add(event("CUTOVER_DECIDED", decision == CutoverDecision.CUTOVER
                ? "DNS and sending-domain checks are verified" : "Keep incumbent delivery active and run verification again"));
        return new OnboardingReport(plan.domain(), zoneId, plan.releaseId(), decision, List.copyOf(events));
    }

    public BuildEvent rotateDkim(String domain, String releaseId) {
        infrai.rotateDkim(domain, releaseId + ":rotate-dkim");
        return event("DKIM_ROTATION_REQUESTED", "DKIM rotation requested for release " + releaseId);
    }

    private void upsert(String zoneId, String type, String name, String content, String operationId) {
        infrai.upsertRecord(zoneId, type, name, content, operationId);
    }

    private static boolean isVerified(Map<String, Object> response) {
        Object verification = response.get("verification");
        if (!(verification instanceof Map<?, ?> map)) return false;
        return "verified".equalsIgnoreCase(String.valueOf(map.get("status")));
    }

    private static String requiredText(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null || value.toString().isBlank()) throw new IllegalStateException("Missing " + field);
        return value.toString();
    }

    private static BuildEvent event(String type, String detail) {
        return new BuildEvent(type, detail, Instant.now().toString());
    }

    public enum CutoverDecision { CUTOVER, HOLD }

    public record DomainPlan(String domain, String releaseId, String spfContent,
                             String dkimType, String dkimName, String dkimContent,
                             String dmarcContent) {}

    public record BuildEvent(String type, String detail, String observedAt) {}

    public record OnboardingReport(String domain, String zoneId, String releaseId,
                                   CutoverDecision decision, List<BuildEvent> events) {}
}
