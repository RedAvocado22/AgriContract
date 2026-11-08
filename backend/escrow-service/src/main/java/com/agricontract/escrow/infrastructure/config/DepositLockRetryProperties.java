package com.agricontract.escrow.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * escrow.deposit-lock.* (milestone-escrow design §8 depositLockRetryMaxAttempts, D39). retryBackoff: wait after
 * the 1st, 2nd, 3rd failure; its size is the number of retries. retryPollInterval: how often due retries are
 * looked for, so a retry is late by at most this much. The demo profile shortens both.
 */
@ConfigurationProperties("escrow.deposit-lock")
public record DepositLockRetryProperties(List<Duration> retryBackoff, Duration retryPollInterval) {
}
