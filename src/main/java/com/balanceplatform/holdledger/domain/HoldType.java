package com.balanceplatform.holdledger.domain;

/**
 * Direction of a card hold.
 *
 * <p>[ASSUMPTION] ФТ-BALANCE-001 §5 mentions that {@code balance-pc} carries "debit/credit
 * card holds" and FR-008 references a {@code holdType} used for an (optionally enabled)
 * direction check against ABS posting {@code direction}, but the exact enum constants are
 * not specified by the ФТ. {@link #DEBIT} / {@link #CREDIT} are assumed as the two values.
 */
public enum HoldType {

    /** Hold created by a debit (outgoing funds) card authorization. */
    DEBIT,

    /** Hold created by a credit (incoming funds, e.g. refund) card authorization. */
    CREDIT
}
