package com.portfolio.outbox.infrastructure;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class SimulatedFailureInjector {
    private double failureRate = 0.0;
    private boolean enabled = false;
    private final Random random;

    public SimulatedFailureInjector() {
        this.random = new Random(42);
    }

    public SimulatedFailureInjector(long seed) {
        this.random = new Random(seed);
    }

    public void configure(double failureRate) {
        this.failureRate = failureRate;
        this.enabled = failureRate > 0.0;
    }

    public void maybeFail(String context) {
        if (enabled && random.nextDouble() < failureRate) {
            throw new RuntimeException("Simulated failure during " + context);
        }
    }

    public void disable() {
        this.enabled = false;
        this.failureRate = 0.0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getFailureRate() {
        return failureRate;
    }
}
