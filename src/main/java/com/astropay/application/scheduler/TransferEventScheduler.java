package com.astropay.application.scheduler;

import com.astropay.application.service.transfer.TransferBatchProcessor;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.outbox.OutboxEventStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TransferEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferEventScheduler.class);
    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 500;
    private static final double HIKARI_SATURATION_THRESHOLD = 0.90;

    private final AtomicInteger currentBatchSize = new AtomicInteger(100);

    private final OutboxEventRepository outboxEventRepository;
    private final TransferBatchProcessor batchProcessor;
    private final MeterRegistry meterRegistry;

    public TransferEventScheduler(OutboxEventRepository outboxEventRepository,
                                  TransferBatchProcessor batchProcessor,
                                  MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.batchProcessor = batchProcessor;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelay = 50) 
    public void scheduleProcessTransferEvents() {
        if (isDatabaseOverloaded()) {
            return; 
        }

        long startTime = System.currentTimeMillis();
        int batchSize = currentBatchSize.get();
        
        var events = findAndLockEvents(batchSize);
        if (events.isEmpty()) {
            return;
        }

        try {
            batchProcessor.processBatch(events);
            adjustBatchSize(System.currentTimeMillis() - startTime, events.size(), batchSize);
        } catch (Exception e) {
            log.error("Critical error during batch processing. Events will be rolled back by transaction.", e);
            currentBatchSize.set(Math.max(MIN_BATCH_SIZE, batchSize / 2));
        }
    }

    private List<OutboxEvent> findAndLockEvents(int batchSize) {
        LocalDateTime lockTimeout = LocalDateTime.now().minusMinutes(1);
        List<OutboxEvent> events = outboxEventRepository.findAndLockUnprocessedEvents(
                OutboxEventStatus.UNPROCESSED, "TransferRequested", lockTimeout, PageRequest.of(0, batchSize));

        if (!events.isEmpty()) {
            LocalDateTime newLockTime = LocalDateTime.now();
            events.forEach(event -> {
                event.setStatus(OutboxEventStatus.PROCESSING);
                event.setLockedAt(newLockTime);
            });
            outboxEventRepository.saveAll(events);
        }
        return events;
    }

    private boolean isDatabaseOverloaded() {
        double activeConnections = getMetricValue("hikaricp.connections.active");
        double maxConnections = getMetricValue("hikaricp.connections.max");

        if (maxConnections > 0) {
            double saturation = activeConnections / maxConnections;
            if (saturation >= HIKARI_SATURATION_THRESHOLD) {
                if (log.isWarnEnabled()) {
                    log.warn("HikariCP saturation at {}%. Threshold is {}%. Skipping cycle.", String.format("%.2f", saturation * 100), HIKARI_SATURATION_THRESHOLD * 100);
                }
                return true;
            }
        }
        return false;
    }

    private double getMetricValue(String metricName) {
        Gauge gauge = meterRegistry.find(metricName).tag("pool", "HikariPool").gauge();
        return gauge != null ? gauge.value() : 0.0;
    }

    private void adjustBatchSize(long durationMillis, int processedCount, int batchSize) {
        long targetDuration = 1000;
        
        if (durationMillis > targetDuration * 1.5) {
            currentBatchSize.updateAndGet(current -> Math.max(MIN_BATCH_SIZE, (int) (current * 0.8)));
            log.info("Batch processing too slow ({}ms). Decreasing batch size to {}.", durationMillis, currentBatchSize.get());
        } else if (durationMillis < targetDuration * 0.5 && processedCount == batchSize) {
            currentBatchSize.updateAndGet(current -> Math.min(MAX_BATCH_SIZE, (int) (current * 1.2)));
            log.info("Batch processing fast ({}ms). Increasing batch size to {}.", durationMillis, currentBatchSize.get());
        }
    }
}
