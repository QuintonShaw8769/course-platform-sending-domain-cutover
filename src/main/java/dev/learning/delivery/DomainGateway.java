package dev.learning.delivery;

import java.util.Map;

public interface DomainGateway {
    Map<String, Object> addZone(String domain, String operationId);
    Map<String, Object> upsertRecord(String zoneId, String type, String name, String content, String operationId);
    Map<String, Object> verifyDns(String domain, String operationId);
    Map<String, Object> verifySendingDomain(String domain, String operationId);
    Map<String, Object> rotateDkim(String domain, String operationId);
}
