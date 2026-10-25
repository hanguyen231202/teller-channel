package com.tellerchannel.coremock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChaosServiceTest {

    @Test
    void failureRateOne_alwaysThrows() {
        ChaosService chaos = new ChaosService(new ChaosSettings(0, 1.0, 1.0));

        assertThrows(CoreUnavailableException.class, chaos::beforePosting);
        assertThrows(CoreUnavailableException.class, chaos::beforeInquiry);
    }

    @Test
    void delay_isApplied() {
        ChaosService chaos = new ChaosService(new ChaosSettings(200, 0, 0));

        long start = System.nanoTime();
        chaos.beforePosting();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs >= 200, "elapsed " + elapsedMs);
    }

    @Test
    void reset_restoresInitialSettings() {
        ChaosService chaos = new ChaosService(ChaosSettings.none());
        chaos.update(new ChaosSettings(5000, 0.5, 0));

        chaos.reset();

        assertEquals(ChaosSettings.none(), chaos.get());
    }
}
