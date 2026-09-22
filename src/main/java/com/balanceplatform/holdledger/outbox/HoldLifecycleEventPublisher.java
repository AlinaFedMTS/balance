package com.balanceplatform.holdledger.outbox;

import com.balanceplatform.holdledger.domain.HoldLifecycleEvent;

/**
 * Port for publishing {@link HoldLifecycleEvent}s to {@code hold.lifecycle.events}.
 *
 * <p>[ASSUMPTION] Per FR-006, publication must go through a transactional outbox so that a
 * process crash between persisting the hold and publishing the event never loses the event.
 * The concrete outbox implementation (table, background publisher with
 * {@code FOR UPDATE SKIP LOCKED}, Kafka producer) is outside the scope of this task; this
 * interface is the port the service layer depends on and must be called within the same
 * transaction as the triggering {@code HoldRepository.save}.
 */
public interface HoldLifecycleEventPublisher {

    /**
     * Enqueues a lifecycle event for reliable, at-least-once, ordered-by-{@code operationId}
     * publication.
     *
     * @param event event to publish
     */
    void publish(HoldLifecycleEvent event);
}
