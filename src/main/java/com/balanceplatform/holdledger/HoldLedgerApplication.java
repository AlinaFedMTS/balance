package com.balanceplatform.holdledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Hold Ledger service (ФТ-BALANCE-001, CONTRACT-hold-ledger-v1).
 *
 * <p>Hold Ledger is the single source of truth for the lifecycle status of card holds
 * (authorizations). It exposes an internal REST API used by the Temporal worker and the
 * reconciliation job to create, release and expire holds, and publishes lifecycle events
 * via a transactional outbox (FR-006). Balance calculation for the client is out of scope
 * (see ФТ-BALANCE-002).
 */
@SpringBootApplication
public class HoldLedgerApplication {

    /**
     * Starts the Hold Ledger Spring Boot application.
     *
     * @param args standard JVM command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(HoldLedgerApplication.class, args);
    }
}
