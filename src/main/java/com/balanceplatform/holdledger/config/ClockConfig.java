package com.balanceplatform.holdledger.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the system {@link Clock} bean used across Hold Ledger for timestamping holds,
 * lifecycle events and error responses, so tests can substitute a fixed clock.
 */
@Configuration
public class ClockConfig {

    /**
     * Exposes the system UTC clock as a Spring bean.
     *
     * @return a system UTC {@link Clock}
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
