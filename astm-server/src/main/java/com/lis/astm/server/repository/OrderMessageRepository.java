package com.lis.astm.server.repository;

import com.lis.astm.server.model.OrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Repository for OrderMessage persistence
 * Handles database operations for message retry and collision detection
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrderMessageRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    private final RowMapper<OrderMessage> rowMapper = (rs, rowNum) -> OrderMessage.builder()
            .id(rs.getLong("Id"))
            .messageId(rs.getString("MessageId"))
            .instrumentName(rs.getString("InstrumentName"))
            .messageContent(rs.getString("MessageContent"))
            .status(OrderMessage.Status.valueOf(rs.getString("Status")))
            .retryCount(rs.getInt("RetryCount"))
            .maxRetryAttempts(rs.getInt("MaxRetryAttempts"))
            .createdAt(rs.getTimestamp("CreatedAt").toLocalDateTime())
            .updatedAt(rs.getTimestamp("UpdatedAt").toLocalDateTime())
            .lastRetryAt(rs.getTimestamp("LastRetryAt") != null ? 
                        rs.getTimestamp("LastRetryAt").toLocalDateTime() : null)
            .nextRetryAt(rs.getTimestamp("NextRetryAt") != null ? 
                        rs.getTimestamp("NextRetryAt").toLocalDateTime() : null)
            .errorMessage(rs.getString("ErrorMessage"))
            .build();
    
    /**
     * Save a new order message
     */
    public OrderMessage save(OrderMessage orderMessage) {
        String sql = "INSERT INTO ASTMOrderMessages " +
                "(MessageId, InstrumentName, MessageContent, Status, RetryCount, MaxRetryAttempts, " +
                "CreatedAt, UpdatedAt, LastRetryAt, NextRetryAt, ErrorMessage) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderMessage.getMessageId());
            ps.setString(2, orderMessage.getInstrumentName());
            ps.setString(3, orderMessage.getMessageContent());
            ps.setString(4, orderMessage.getStatus().name());
            ps.setInt(5, orderMessage.getRetryCount());
            ps.setInt(6, orderMessage.getMaxRetryAttempts());
            ps.setTimestamp(7, Timestamp.valueOf(orderMessage.getCreatedAt()));
            ps.setTimestamp(8, Timestamp.valueOf(orderMessage.getUpdatedAt()));
            ps.setTimestamp(9, orderMessage.getLastRetryAt() != null ? 
                           Timestamp.valueOf(orderMessage.getLastRetryAt()) : null);
            ps.setTimestamp(10, orderMessage.getNextRetryAt() != null ? 
                           Timestamp.valueOf(orderMessage.getNextRetryAt()) : null);
            ps.setString(11, orderMessage.getErrorMessage());
            return ps;
        }, keyHolder);
        
        orderMessage.setId(keyHolder.getKey().longValue());
        return orderMessage;
    }
    
    /**
     * Update an existing order message
     */
    public void update(OrderMessage orderMessage) {
        String sql = "UPDATE ASTMOrderMessages SET " +
                "Status = ?, RetryCount = ?, UpdatedAt = ?, " +
                "LastRetryAt = ?, NextRetryAt = ?, ErrorMessage = ? " +
                "WHERE Id = ?";
        
        jdbcTemplate.update(sql,
                orderMessage.getStatus().name(),
                orderMessage.getRetryCount(),
                Timestamp.valueOf(orderMessage.getUpdatedAt()),
                orderMessage.getLastRetryAt() != null ? 
                    Timestamp.valueOf(orderMessage.getLastRetryAt()) : null,
                orderMessage.getNextRetryAt() != null ? 
                    Timestamp.valueOf(orderMessage.getNextRetryAt()) : null,
                orderMessage.getErrorMessage(),
                orderMessage.getId());
    }
    
    /**
     * Find message by ID
     */
    public Optional<OrderMessage> findById(Long id) {
        String sql = "SELECT * FROM ASTMOrderMessages WHERE Id = ?";
        try {
            OrderMessage message = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.ofNullable(message);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find message by message ID
     */
    public Optional<OrderMessage> findByMessageId(String messageId) {
        String sql = "SELECT * FROM ASTMOrderMessages WHERE MessageId = ?";
        try {
            OrderMessage message = jdbcTemplate.queryForObject(sql, rowMapper, messageId);
            return Optional.ofNullable(message);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find messages ready for retry processing
     * Returns PENDING messages where NextRetryAt is null or in the past
     */
    public List<OrderMessage> findMessagesReadyForRetry(int limit) {
        String sql = "SELECT TOP (?) * FROM ASTMOrderMessages " +
                "WHERE Status = 'PENDING' " +
                "AND RetryCount < MaxRetryAttempts " +
                "AND (NextRetryAt IS NULL OR NextRetryAt <= GETDATE()) " +
                "ORDER BY CreatedAt ASC";
        
        return jdbcTemplate.query(sql, rowMapper, limit);
    }
    
    /**
     * Find pending messages for a specific instrument
     */
    public List<OrderMessage> findPendingByInstrument(String instrumentName) {
        String sql = "SELECT * FROM ASTMOrderMessages " +
                "WHERE InstrumentName = ? " +
                "AND Status IN ('PENDING', 'PROCESSING') " +
                "ORDER BY CreatedAt ASC";
        
        return jdbcTemplate.query(sql, rowMapper, instrumentName);
    }
    
    /**
     * Mark message as processing (atomic operation to prevent concurrent processing)
     */
    public boolean markAsProcessing(Long id) {
        String sql = "UPDATE ASTMOrderMessages " +
                "SET Status = 'PROCESSING', UpdatedAt = GETDATE() " +
                "WHERE Id = ? AND Status = 'PENDING'";
        
        int rowsUpdated = jdbcTemplate.update(sql, id);
        return rowsUpdated > 0;
    }
    
    /**
     * Get statistics for monitoring
     */
    public OrderMessageStats getStats() {
        String sql = "SELECT Status, COUNT(*) as count FROM ASTMOrderMessages GROUP BY Status";
        
        OrderMessageStats stats = new OrderMessageStats();
        
        jdbcTemplate.query(sql, rs -> {
            String status = rs.getString("Status");
            int count = rs.getInt("count");
            
            switch (status) {
                case "PENDING":
                    stats.setPending(count);
                    break;
                case "PROCESSING":
                    stats.setProcessing(count);
                    break;
                case "SUCCESS":
                    stats.setSuccess(count);
                    break;
                case "FAILED":
                    stats.setFailed(count);
                    break;
            }
        });
        
        return stats;
    }
    
    /**
     * Clean up old successful messages (optional - for maintenance)
     */
    public int cleanupOldSuccessfulMessages(int daysOld) {
        String sql = "DELETE FROM ASTMOrderMessages " +
                "WHERE Status = 'SUCCESS' " +
                "AND UpdatedAt < DATEADD(day, -?, GETDATE())";
        
        return jdbcTemplate.update(sql, daysOld);
    }
    
    /**
     * Statistics class for monitoring
     */
    public static class OrderMessageStats {
        private int pending;
        private int processing;
        private int success;
        private int failed;
        
        // Getters and setters
        public int getPending() { return pending; }
        public void setPending(int pending) { this.pending = pending; }
        
        public int getProcessing() { return processing; }
        public void setProcessing(int processing) { this.processing = processing; }
        
        public int getSuccess() { return success; }
        public void setSuccess(int success) { this.success = success; }
        
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        
        public int getTotal() { return pending + processing + success + failed; }
        
        @Override
        public String toString() {
            return String.format("OrderMessageStats{pending=%d, processing=%d, success=%d, failed=%d, total=%d}",
                    pending, processing, success, failed, getTotal());
        }
    }
}
