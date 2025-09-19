package com.lis.astm.server.repository;

import com.lis.astm.server.model.ServerMessage;
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
 * JDBC Repository for ServerMessage persistence
 * Handles database operations for incoming ASTM messages from instruments
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ServerMessageRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    private final RowMapper<ServerMessage> rowMapper = (rs, rowNum) -> ServerMessage.builder()
            .id(rs.getLong("Id"))
            .messageId(rs.getString("MessageId"))
            .instrumentName(rs.getString("InstrumentName"))
            .rawMessage(rs.getString("RawMessage"))
            .messageType(rs.getString("MessageType"))
            .status(ServerMessage.Status.valueOf(rs.getString("Status")))
            .receivedAt(rs.getTimestamp("ReceivedAt").toLocalDateTime())
            .processedAt(rs.getTimestamp("ProcessedAt") != null ? 
                        rs.getTimestamp("ProcessedAt").toLocalDateTime() : null)
            .publishedAt(rs.getTimestamp("PublishedAt") != null ? 
                        rs.getTimestamp("PublishedAt").toLocalDateTime() : null)
            .errorMessage(rs.getString("ErrorMessage"))
            .remoteAddress(rs.getString("RemoteAddress"))
            .build();
    
    /**
     * Save a new incoming server message
     */
    public ServerMessage save(ServerMessage serverMessage) {
        String sql = "INSERT INTO ASTMServerMessages " +
                "(MessageId, InstrumentName, RawMessage, MessageType, Status, ReceivedAt, " +
                "ProcessedAt, PublishedAt, ErrorMessage, RemoteAddress) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, serverMessage.getMessageId());
            ps.setString(2, serverMessage.getInstrumentName());
            ps.setString(3, serverMessage.getRawMessage());
            ps.setString(4, serverMessage.getMessageType());
            ps.setString(5, serverMessage.getStatus().name());
            ps.setTimestamp(6, Timestamp.valueOf(serverMessage.getReceivedAt()));
            ps.setTimestamp(7, serverMessage.getProcessedAt() != null ? 
                           Timestamp.valueOf(serverMessage.getProcessedAt()) : null);
            ps.setTimestamp(8, serverMessage.getPublishedAt() != null ? 
                           Timestamp.valueOf(serverMessage.getPublishedAt()) : null);
            ps.setString(9, serverMessage.getErrorMessage());
            ps.setString(10, serverMessage.getRemoteAddress());
            return ps;
        }, keyHolder);
        
        serverMessage.setId(keyHolder.getKey().longValue());
        return serverMessage;
    }
    
    /**
     * Update an existing server message
     */
    public void update(ServerMessage serverMessage) {
        String sql = "UPDATE ASTMServerMessages SET " +
                "Status = ?, ProcessedAt = ?, PublishedAt = ?, ErrorMessage = ? " +
                "WHERE Id = ?";
        
        jdbcTemplate.update(sql,
                serverMessage.getStatus().name(),
                serverMessage.getProcessedAt() != null ? 
                    Timestamp.valueOf(serverMessage.getProcessedAt()) : null,
                serverMessage.getPublishedAt() != null ? 
                    Timestamp.valueOf(serverMessage.getPublishedAt()) : null,
                serverMessage.getErrorMessage(),
                serverMessage.getId());
    }
    
    /**
     * Find message by ID
     */
    public Optional<ServerMessage> findById(Long id) {
        String sql = "SELECT * FROM ASTMServerMessages WHERE Id = ?";
        try {
            ServerMessage message = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.ofNullable(message);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find message by message ID
     */
    public Optional<ServerMessage> findByMessageId(String messageId) {
        String sql = "SELECT * FROM ASTMServerMessages WHERE MessageId = ?";
        try {
            ServerMessage message = jdbcTemplate.queryForObject(sql, rowMapper, messageId);
            return Optional.ofNullable(message);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find recent messages for an instrument
     */
    public List<ServerMessage> findRecentByInstrument(String instrumentName, int limit) {
        String sql = "SELECT TOP (?) * FROM ASTMServerMessages " +
                "WHERE InstrumentName = ? " +
                "ORDER BY ReceivedAt DESC";
        
        return jdbcTemplate.query(sql, rowMapper, limit, instrumentName);
    }
    
    /**
     * Find messages by status
     */
    public List<ServerMessage> findByStatus(ServerMessage.Status status, int limit) {
        String sql = "SELECT TOP (?) * FROM ASTMServerMessages " +
                "WHERE Status = ? " +
                "ORDER BY ReceivedAt DESC";
        
        return jdbcTemplate.query(sql, rowMapper, limit, status.name());
    }
    
    /**
     * Get statistics for monitoring
     */
    public ServerMessageStats getStats() {
        String sql = "SELECT Status, COUNT(*) as count FROM ASTMServerMessages GROUP BY Status";
        
        ServerMessageStats stats = new ServerMessageStats();
        
        jdbcTemplate.query(sql, rs -> {
            String status = rs.getString("Status");
            int count = rs.getInt("count");
            
            switch (status) {
                case "RECEIVED":
                    stats.setReceived(count);
                    break;
                case "PROCESSED":
                    stats.setProcessed(count);
                    break;
                case "PUBLISHED":
                    stats.setPublished(count);
                    break;
                case "ERROR":
                    stats.setError(count);
                    break;
            }
        });
        
        return stats;
    }
    
    /**
     * Clean up old messages (for maintenance)
     */
    public int cleanupOldMessages(int daysOld) {
        String sql = "DELETE FROM ASTMServerMessages " +
                "WHERE Status = 'PUBLISHED' " +
                "AND PublishedAt < DATEADD(day, -?, GETDATE())";
        
        return jdbcTemplate.update(sql, daysOld);
    }
    
    /**
     * Statistics class for monitoring
     */
    public static class ServerMessageStats {
        private int received;
        private int processed;
        private int published;
        private int error;
        
        // Getters and setters
        public int getReceived() { return received; }
        public void setReceived(int received) { this.received = received; }
        
        public int getProcessed() { return processed; }
        public void setProcessed(int processed) { this.processed = processed; }
        
        public int getPublished() { return published; }
        public void setPublished(int published) { this.published = published; }
        
        public int getError() { return error; }
        public void setError(int error) { this.error = error; }
        
        public int getTotal() { return received + processed + published + error; }
        
        @Override
        public String toString() {
            return String.format("ServerMessageStats{received=%d, processed=%d, published=%d, error=%d, total=%d}",
                    received, processed, published, error, getTotal());
        }
    }
}
