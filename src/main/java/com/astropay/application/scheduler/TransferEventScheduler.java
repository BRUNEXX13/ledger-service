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
    private static final int MIN_BATCH_SIZE = 50; // Aumentado o mínimo para evitar fragmentação excessiva
    private static final int MAX_BATCH_SIZE = 1000; // Aumentado para permitir maior vazão em picos
    private static final double HIKARI_SATURATION_THRESHOLD = 0.90;

    private final AtomicInteger currentBatchSize = new AtomicInteger(200); // Start mais agressivo

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

    // TUNING: Reduzido para 20ms para processamento quase em tempo real
    @Scheduled(fixedDelay = 20) 
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
            log.error("Critical error during batch processing. Reverting event status to UNPROCESSED.", e);
            unlockEvents(events);
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

    private void unlockEvents(List<OutboxEvent> events) {
        try {
            events.forEach(event -> {
                event.setStatus(OutboxEventStatus.UNPROCESSED);
                event.setLockedAt(null);
                event.incrementRetryCount(); // Increment retry count to avoid infinite loops on poison pills
            });
            outboxEventRepository.saveAll(events);
        } catch (Exception ex) {
            log.error("Failed to unlock events after batch failure. These events might be stuck in PROCESSING state until timeout.", ex);
        }
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
        long targetDuration = 500; // Meta mais agressiva de 500ms por lote
        
        if (durationMillis > targetDuration * 1.5) {
            currentBatchSize.updateAndGet(current -> Math.max(MIN_BATCH_SIZE, (int) (current * 0.8)));
            // Log apenas se a mudança for significativa para evitar ruído
            if (currentBatchSize.get() < batchSize) {
                log.info("Batch processing too slow ({}ms). Decreasing batch size to {}.", durationMillis, currentBatchSize.get());
            }
        } else if (durationMillis < targetDuration * 0.5 && processedCount == batchSize) {
            currentBatchSize.updateAndGet(current -> Math.min(MAX_BATCH_SIZE, (int) (current * 1.2)));
        }
    }
}
