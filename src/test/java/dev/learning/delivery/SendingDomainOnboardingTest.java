package dev.learning.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.learning.delivery.SendingDomainOnboarding.CutoverDecision;
import dev.learning.delivery.SendingDomainOnboarding.DomainPlan;

public final class SendingDomainOnboardingTest {
    public static void main(String[] args) {
        verifiedRecordsPermitCutover();
        pendingMailCheckKeepsIncumbentActive();
        System.out.println("SendingDomainOnboardingTest: 2 decisions passed");
    }

    private static void verifiedRecordsPermitCutover() {
        FakeGateway gateway = new FakeGateway(true, true);
        var report = new SendingDomainOnboarding(gateway).onboard(plan("release-42"));
        check(report.decision() == CutoverDecision.CUTOVER, "verified setup should permit cutover");
        check(gateway.records.equals(List.of("TXT:@", "CNAME:learn1._domainkey", "TXT:_dmarc")),
                "records should be written in teaching order");
        check(gateway.operationIds.stream().distinct().count() == gateway.operationIds.size(),
                "each write should have a stable, distinct operation id");
    }

    private static void pendingMailCheckKeepsIncumbentActive() {
        FakeGateway gateway = new FakeGateway(true, false);
        var report = new SendingDomainOnboarding(gateway).onboard(plan("release-43"));
        check(report.decision() == CutoverDecision.HOLD, "pending mail verification should hold cutover");
        check(report.events().get(report.events().size() - 1).detail().contains("incumbent"),
                "diagnostic should name the rollback posture");
    }

    private static DomainPlan plan(String releaseId) {
        return new DomainPlan("mail.academy.example", releaseId, "v=spf1 include:sender.example -all",
                "CNAME", "learn1._domainkey", "selector.sender.example", "v=DMARC1; p=none");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGateway implements DomainGateway {
        private final boolean dnsVerified;
        private final boolean mailVerified;
        private final List<String> records = new ArrayList<>();
        private final List<String> operationIds = new ArrayList<>();

        FakeGateway(boolean dnsVerified, boolean mailVerified) {
            this.dnsVerified = dnsVerified;
            this.mailVerified = mailVerified;
        }

        public Map<String, Object> addZone(String domain, String operationId) {
            operationIds.add(operationId);
            return Map.of("zone_id", "zone-course-mail");
        }

        public Map<String, Object> upsertRecord(String zoneId, String type, String name, String content, String operationId) {
            records.add(type + ":" + name);
            operationIds.add(operationId);
            return Map.of("record_id", operationId);
        }

        public Map<String, Object> verifyDns(String domain, String operationId) {
            operationIds.add(operationId);
            return status(dnsVerified);
        }

        public Map<String, Object> verifySendingDomain(String domain, String operationId) {
            operationIds.add(operationId);
            return status(mailVerified);
        }

        public Map<String, Object> rotateDkim(String domain, String operationId) {
            operationIds.add(operationId);
            return Map.of("domain", domain);
        }

        private Map<String, Object> status(boolean verified) {
            return Map.of("verification", Map.of("status", verified ? "verified" : "pending"));
        }
    }
}
