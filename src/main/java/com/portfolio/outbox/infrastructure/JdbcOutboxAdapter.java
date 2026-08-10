package com.portfolio.outbox.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.outbox.application.port.OrderTransaction;
import com.portfolio.outbox.application.port.OutboxStore;
import com.portfolio.outbox.application.port.ProcessedEventStore;
import com.portfolio.outbox.domain.CommerceEvent;
import com.portfolio.outbox.domain.Order;
import com.portfolio.outbox.domain.OutboxEvent;
import com.portfolio.outbox.domain.OutboxStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JdbcOutboxAdapter implements OrderTransaction, OutboxStore, ProcessedEventStore {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public JdbcOutboxAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = objectMapper;
    }

    @Override
    public void persist(Order order, CommerceEvent event) {
        transaction.executeWithoutResult(ignored -> {
            jdbc.update(
                    "INSERT INTO orders(order_id, payload, created_at) VALUES (?, ?::jsonb, ?)",
                    order.id(), writeJson(order.payload()), Timestamp.from(order.createdAt())
            );
            jdbc.update("""
                    INSERT INTO outbox_event(
                        event_id, event_type, event_version, aggregate_id, saga_id,
                        correlation_id, causation_id, occurred_at, payload, status, next_attempt_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?)
                    """,
                    event.eventId(), event.eventType(), event.eventVersion(), event.aggregateId(), event.sagaId(),
                    event.correlationId(), event.causationId(), Timestamp.from(event.occurredAt()),
                    writeJson(event.payload()), Timestamp.from(event.occurredAt())
            );
        });
    }

    @Override
    public List<OutboxEvent> claimBatch(String workerId, int batchSize, Duration leaseDuration) {
        String sql = """
                WITH candidates AS (
                    SELECT event_id
                    FROM outbox_event
                    WHERE (
                        status IN ('PENDING', 'FAILED') AND next_attempt_at <= clock_timestamp()
                    ) OR (
                        status = 'PROCESSING' AND lease_until < clock_timestamp()
                    )
                    ORDER BY attempts ASC, occurred_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_event AS event
                SET status = 'PROCESSING',
                    lease_owner = ?,
                    lease_until = clock_timestamp() + (? * interval '1 millisecond'),
                    attempts = event.attempts + 1,
                    last_error = NULL
                FROM candidates
                WHERE event.event_id = candidates.event_id
                RETURNING event.*
                """;
        return jdbc.query(sql, this::mapOutboxEvent, batchSize, workerId, leaseDuration.toMillis());
    }

    @Override
    public void markPublished(UUID eventId, String workerId, Instant publishedAt) {
        int updated = jdbc.update("""
                UPDATE outbox_event
                SET status = 'PUBLISHED', published_at = ?, lease_owner = NULL, lease_until = NULL
                WHERE event_id = ? AND status = 'PROCESSING' AND lease_owner = ?
                """, Timestamp.from(publishedAt), eventId, workerId);
        requireSingleUpdate(updated, eventId, "publish");
    }

    @Override
    public void markFailed(UUID eventId, String workerId, Instant nextAttemptAt, String error) {
        int updated = jdbc.update("""
                UPDATE outbox_event
                SET status = 'FAILED', next_attempt_at = ?, last_error = ?, lease_owner = NULL, lease_until = NULL
                WHERE event_id = ? AND status = 'PROCESSING' AND lease_owner = ?
                """, Timestamp.from(nextAttemptAt), error, eventId, workerId);
        requireSingleUpdate(updated, eventId, "fail");
    }

    @Override
    public boolean recordIfFirst(UUID eventId, Instant processedAt) {
        return jdbc.update("""
                INSERT INTO processed_event(event_id, processed_at)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, Timestamp.from(processedAt)) == 1;
    }

    public void reset() {
        jdbc.execute("TRUNCATE TABLE processed_event, outbox_event, orders");
    }

    public int orderCount() {
        return requiredCount("SELECT COUNT(*) FROM orders");
    }

    public int outboxCount() {
        return requiredCount("SELECT COUNT(*) FROM outbox_event");
    }

    public int processedEventCount() {
        return requiredCount("SELECT COUNT(*) FROM processed_event");
    }

    public long statusCount(OutboxStatus status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE status = ?",
                Long.class,
                status.name()
        );
        return count == null ? 0 : count;
    }

    public List<EventMeasurement> eventMeasurements() {
        return jdbc.query("""
                SELECT event_id, attempts,
                       EXTRACT(EPOCH FROM (published_at - occurred_at)) * 1000 AS publish_lag_ms
                FROM outbox_event
                ORDER BY occurred_at
                """, (rs, rowNum) -> new EventMeasurement(
                rs.getObject("event_id", UUID.class),
                rs.getInt("attempts"),
                rs.getDouble("publish_lag_ms")
        ));
    }

    private OutboxEvent mapOutboxEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        CommerceEvent event = new CommerceEvent(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("event_version"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getObject("saga_id", UUID.class),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getObject("causation_id", UUID.class),
                resultSet.getTimestamp("occurred_at").toInstant(),
                readJson(resultSet.getString("payload"))
        );
        return new OutboxEvent(event, OutboxStatus.valueOf(resultSet.getString("status")), resultSet.getInt("attempts"));
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload is not valid JSON", exception);
        }
    }

    private Map<String, Object> readJson(String value) throws SQLException {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored payload is not valid JSON", exception);
        }
    }

    private int requiredCount(String sql) {
        Integer count = jdbc.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private void requireSingleUpdate(int updated, UUID eventId, String transition) {
        if (updated != 1) {
            throw new IllegalStateException("Could not " + transition + " leased event " + eventId);
        }
    }

    public record EventMeasurement(UUID eventId, int attempts, double publishLagMs) {
    }
}
