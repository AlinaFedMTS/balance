package com.balanceplatform.holdledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balanceplatform.holdledger.domain.Hold;
import com.balanceplatform.holdledger.domain.HoldLifecycleEvent;
import com.balanceplatform.holdledger.domain.HoldLifecycleEventType;
import com.balanceplatform.holdledger.domain.HoldStatus;
import com.balanceplatform.holdledger.domain.HoldType;
import com.balanceplatform.holdledger.dto.CreateHoldRequest;
import com.balanceplatform.holdledger.dto.ReleaseHoldRequest;
import com.balanceplatform.holdledger.exception.ConflictTerminalException;
import com.balanceplatform.holdledger.exception.HoldNotFoundException;
import com.balanceplatform.holdledger.exception.IdempotencyConflictException;
import com.balanceplatform.holdledger.exception.ThresholdTooLowException;
import com.balanceplatform.holdledger.outbox.HoldLifecycleEventPublisher;
import com.balanceplatform.holdledger.repository.HoldRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link HoldLedgerService} covering the state-machine and idempotency rules of
 * ФТ-BALANCE-001 (FR-001, FR-002, FR-003, FR-005, FR-010, FR-011).
 */
@ExtendWith(MockitoExtension.class)
class HoldLedgerServiceTest {

    private static final String OPERATION_ID = "op-7f3e1c2a";
    private static final String ACCOUNT_ID = "40817810000000000001";
    private static final BigDecimal AMOUNT = new BigDecimal("1500.00");
    private static final String CURRENCY = "RUB";
    private static final Instant FIXED_NOW = Instant.parse("2026-09-21T16:00:00Z");
    private static final int RECONCILIATION_BATCH_SIZE = 500;

    @Mock
    private HoldRepository holdRepository;

    @Mock
    private HoldLifecycleEventPublisher eventPublisher;

    private HoldLedgerService holdLedgerService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        holdLedgerService = new HoldLedgerService(holdRepository, eventPublisher, fixedClock, RECONCILIATION_BATCH_SIZE);
    }

    private CreateHoldRequest createHoldRequest() {
        return CreateHoldRequest.builder()
                .operationId(OPERATION_ID)
                .accountId(ACCOUNT_ID)
                .amount(AMOUNT)
                .currency(CURRENCY)
                .cardId("tok_9f8e7d6c")
                .rrn("123456789012")
                .holdType(HoldType.DEBIT)
                .authorizedAt(FIXED_NOW.minusSeconds(60))
                .build();
    }

    private Hold pendingHold() {
        return Hold.builder()
                .operationId(OPERATION_ID)
                .accountId(ACCOUNT_ID)
                .amount(AMOUNT)
                .currency(CURRENCY)
                .cardId("tok_9f8e7d6c")
                .rrn("123456789012")
                .holdType(HoldType.DEBIT)
                .status(HoldStatus.PENDING)
                .authorizedAt(FIXED_NOW.minusSeconds(60))
                .build();
    }

    @Test
    void createHold_whenOperationIdIsNew_thenPersistsHoldAndPublishesCreatedEvent() {
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.empty());
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HoldCreationResult result = holdLedgerService.createHold(createHoldRequest());

        assertThat(result.created()).isTrue();
        assertThat(result.hold().getStatus()).isEqualTo(HoldStatus.PENDING);
        assertThat(result.hold().getOperationId()).isEqualTo(OPERATION_ID);

        ArgumentCaptor<HoldLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(HoldLifecycleEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(HoldLifecycleEventType.HOLD_CREATED);
        assertThat(eventCaptor.getValue().operationId()).isEqualTo(OPERATION_ID);
    }

    @Test
    void createHold_whenDuplicateWithSameAttributes_thenReturnsExistingHoldWithoutPublishingEvent() {
        Hold existing = pendingHold();
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(existing));

        HoldCreationResult result = holdLedgerService.createHold(createHoldRequest());

        assertThat(result.created()).isFalse();
        assertThat(result.hold()).isEqualTo(existing);
        verify(holdRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void createHold_whenDuplicateWithDifferentAmount_thenThrowsIdempotencyConflictException() {
        Hold existing = pendingHold().toBuilder().amount(new BigDecimal("999.00")).build();
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> holdLedgerService.createHold(createHoldRequest()))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(holdRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void release_whenHoldIsPendingAndMatched_thenTransitionsToMatchedAndPublishesEvent() {
        Hold hold = pendingHold();
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(hold));
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReleaseHoldRequest request = ReleaseHoldRequest.builder()
                .status(HoldStatus.MATCHED_IN_ABS)
                .reason("ABS_POSTED")
                .documentId("doc-556677")
                .build();

        HoldReleaseResult result = holdLedgerService.release(OPERATION_ID, request);

        assertThat(result.stateChanged()).isTrue();
        assertThat(result.hold().getStatus()).isEqualTo(HoldStatus.MATCHED_IN_ABS);
        assertThat(result.hold().getDocumentId()).isEqualTo("doc-556677");
        assertThat(result.hold().getResolvedAt()).isEqualTo(FIXED_NOW);

        ArgumentCaptor<HoldLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(HoldLifecycleEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(HoldLifecycleEventType.HOLD_MATCHED);
    }

    @Test
    void release_whenHoldIsPendingAndReversed_thenTransitionsToReversedAndPublishesEvent() {
        Hold hold = pendingHold();
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(hold));
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReleaseHoldRequest request = ReleaseHoldRequest.builder()
                .status(HoldStatus.REVERSED)
                .reason("PC_REVERSE")
                .build();

        HoldReleaseResult result = holdLedgerService.release(OPERATION_ID, request);

        assertThat(result.stateChanged()).isTrue();
        assertThat(result.hold().getStatus()).isEqualTo(HoldStatus.REVERSED);

        ArgumentCaptor<HoldLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(HoldLifecycleEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(HoldLifecycleEventType.HOLD_REVERSED);
    }

    @Test
    void release_whenSameTerminalStatusRepeated_thenReturnsNoOpWithoutPublishingEvent() {
        Hold reversedHold = pendingHold().release(HoldStatus.REVERSED, "PC_REVERSE", null, FIXED_NOW);
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(reversedHold));

        ReleaseHoldRequest request = ReleaseHoldRequest.builder()
                .status(HoldStatus.REVERSED)
                .reason("PC_REVERSE")
                .build();

        HoldReleaseResult result = holdLedgerService.release(OPERATION_ID, request);

        assertThat(result.stateChanged()).isFalse();
        assertThat(result.hold()).isEqualTo(reversedHold);
        verify(holdRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void release_whenDifferentTerminalStatusRequested_thenThrowsConflictTerminalException() {
        Hold reversedHold = pendingHold().release(HoldStatus.REVERSED, "PC_REVERSE", null, FIXED_NOW);
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(reversedHold));

        ReleaseHoldRequest request = ReleaseHoldRequest.builder()
                .status(HoldStatus.MATCHED_IN_ABS)
                .reason("ABS_POSTED")
                .documentId("doc-1")
                .build();

        assertThatThrownBy(() -> holdLedgerService.release(OPERATION_ID, request))
                .isInstanceOf(ConflictTerminalException.class);
        verify(holdRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void release_whenHoldNotFound_thenThrowsHoldNotFoundException() {
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.empty());

        ReleaseHoldRequest request = ReleaseHoldRequest.builder()
                .status(HoldStatus.REVERSED)
                .reason("PC_REVERSE")
                .build();

        assertThatThrownBy(() -> holdLedgerService.release(OPERATION_ID, request))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void expireStalePendingHolds_whenThresholdBelowMinimum_thenThrowsThresholdTooLowException() {
        assertThatThrownBy(() -> holdLedgerService.expireStalePendingHolds(100))
                .isInstanceOf(ThresholdTooLowException.class);
        verify(holdRepository, never()).findPendingAuthorizedBefore(any(), eq(RECONCILIATION_BATCH_SIZE));
    }

    @Test
    void expireStalePendingHolds_whenStaleHoldsExist_thenExpiresAllAndPublishesEvents() {
        Hold staleHold1 = pendingHold();
        Hold staleHold2 = pendingHold().toBuilder().operationId("op-other").build();
        when(holdRepository.findPendingAuthorizedBefore(any(Instant.class), eq(RECONCILIATION_BATCH_SIZE)))
                .thenReturn(List.of(staleHold1, staleHold2));
        when(holdRepository.save(any(Hold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpireStaleResult result = holdLedgerService.expireStalePendingHolds(216);

        assertThat(result.expiredHolds()).hasSize(2);
        assertThat(result.expiredHolds()).allMatch(hold -> hold.getStatus() == HoldStatus.EXPIRED);
        verify(eventPublisher, times(2)).publish(any(HoldLifecycleEvent.class));
    }

    @Test
    void expireStalePendingHolds_whenNoStaleHolds_thenReturnsEmptyResultWithoutPublishing() {
        when(holdRepository.findPendingAuthorizedBefore(any(Instant.class), eq(RECONCILIATION_BATCH_SIZE)))
                .thenReturn(List.of());

        ExpireStaleResult result = holdLedgerService.expireStalePendingHolds(216);

        assertThat(result.expiredHolds()).isEmpty();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void getHold_whenExists_thenReturnsHold() {
        Hold hold = pendingHold();
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.of(hold));

        Hold result = holdLedgerService.getHold(OPERATION_ID);

        assertThat(result).isEqualTo(hold);
    }

    @Test
    void getHold_whenNotExists_thenThrowsHoldNotFoundException() {
        when(holdRepository.findByOperationId(OPERATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdLedgerService.getHold(OPERATION_ID))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void searchHolds_whenCalled_thenDelegatesToRepository() {
        Hold hold = pendingHold();
        when(holdRepository.search("123456789012", ACCOUNT_ID, HoldStatus.PENDING)).thenReturn(List.of(hold));

        List<Hold> result = holdLedgerService.searchHolds("123456789012", ACCOUNT_ID, HoldStatus.PENDING);

        assertThat(result).containsExactly(hold);
    }
}
