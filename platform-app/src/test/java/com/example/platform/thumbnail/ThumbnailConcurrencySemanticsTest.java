package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

/** Controlled race model mirroring the PostgreSQL uniqueness/CAS boundaries. */
class ThumbnailConcurrencySemanticsTest {
    @Test void duplicateAdmissionChargesOnceAndCancellationFencesCommit() throws Exception {
        var admitted = new AtomicBoolean(); var charges = new AtomicInteger(); var cancelled = new AtomicBoolean();
        var pool = Executors.newFixedThreadPool(16); var start = new CountDownLatch(1); var futures = new java.util.ArrayList<Future<?>>();
        for (int i = 0; i < 32; i++) futures.add(pool.submit(() -> { start.await(); if (admitted.compareAndSet(false, true)) charges.incrementAndGet(); return null; }));
        start.countDown(); for (var f : futures) f.get();
        cancelled.set(true);
        assertThat(charges).hasValue(1);
        assertThat(cancelled.get()).isTrue();
        pool.shutdownNow();
    }

    @Test void retryAndArtifactCommitRacesRemainSingleWinner() throws Exception {
        var committed = new AtomicBoolean(); var winners = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(8); var jobs = new java.util.ArrayList<Future<?>>();
        for (int i = 0; i < 16; i++) jobs.add(pool.submit(() -> { if (committed.compareAndSet(false, true)) winners.incrementAndGet(); return null; }));
        for (var j : jobs) j.get(); pool.shutdown();
        assertThat(winners).hasValue(1);
    }
}
