package com.balanceplatform.holdledger.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Lifecycle event published to {@code hold.lifecycle.events} via the transactional outbox
 * (FR-006, ФТ-BALANCE-001 §6).
 *
 * @param eventId     unique identifier used by downstream consumers for deduplication (FR-006, FR-A03)
 * @param eventType   discriminator describing which transition occurred
 * @param operationId business key of the hold this event refers to
 * @param status      resulting {@link HoldStatus} of the hold
 * @param reason      reason associated with the transition, {@code null} for {@link HoldLifecycleEventType#HOLD_CREATED}
 * @param documentId  ABS document id, only populated for {@link HoldLifecycleEventType#HOLD_MATCHED}
 * @param occurredAt  timestamp at which the transition occurred
 */
@Builder
public record HoldLifecycleEvent(
        UUID eventId,
        HoldLifecycleEventType eventType,
        String operationId,
        HoldStatus status,
        String reason,
        String documentId,
        Instant occurredAt) {

    /**
     * Builds a lifecycle event from the current state of a {@link Hold}.
     *
     * @param eventType  discriminator for the transition that occurred
     * @param hold       hold state after the transition
     * @param documentId ABS document id to attach, may be {@code null}
     * @return a new {@link HoldLifecycleEvent} with a fresh {@link #eventId}
     */
    public static HoldLifecycleEvent of(HoldLifecycleEventType eventType, Hold hold, String documentId) {
        return HoldLifecycleEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .operationId(hold.getOperationId())
                .status(hold.getStatus())
                .reason(hold.getReason())
                .documentId(documentId)
                .occurredAt(hold.getResolvedAt() != null ? hold.getResolvedAt() : hold.getAuthorizedAt())
                .build();
    }
}
