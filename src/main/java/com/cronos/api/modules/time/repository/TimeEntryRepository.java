package com.cronos.api.modules.time.repository;

import com.cronos.api.core.DatabaseManager;
import com.cronos.api.modules.time.model.TimeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TimeEntryRepository {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryRepository.class);
    private final DatabaseManager dbManager;

    public TimeEntryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public TimeEntry findActiveTimer(Integer workspaceId, Integer userId) {
        String sql = "SELECT * FROM `time_entry` WHERE workspace_id = ? AND user_id = ? AND end_time IS NULL";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, workspaceId);
            stmt.setInt(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TimeEntry t = new TimeEntry();
                    t.setId(rs.getInt("id"));
                    t.setWorkspaceId(rs.getInt("workspace_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setProjectId(rs.getObject("project_id", Integer.class));
                    t.setTaskId(rs.getObject("task_id", Integer.class));
                    t.setDescription(rs.getString("description"));
                    t.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
                    t.setTagIds(getTagIdsForEntry(conn, t.getId()));
                    
                    return t;
                }
            }
        } catch (SQLException e) {
            log.error("Error al buscar cronómetro activo", e);
        }
        return null;
    }

    public TimeEntry startTimer(TimeEntry entry, List<Integer> tagIds) {
        String insertEntrySql = "INSERT INTO `time_entry` (workspace_id, user_id, project_id, task_id, description, start_time) VALUES (?, ?, ?, ?, ?, ?)";
        String insertTagSql = "INSERT INTO `time_entry_tag` (time_entry_id, tag_id) VALUES (?, ?)";

        Connection conn = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Iniciar transacción

            // 1. Insertar TimeEntry
            try (PreparedStatement entryStmt = conn.prepareStatement(insertEntrySql, Statement.RETURN_GENERATED_KEYS)) {
                entryStmt.setInt(1, entry.getWorkspaceId());
                entryStmt.setInt(2, entry.getUserId());
                entryStmt.setObject(3, entry.getProjectId(), Types.INTEGER);
                entryStmt.setObject(4, entry.getTaskId(), Types.INTEGER);
                entryStmt.setString(5, entry.getDescription());
                entryStmt.setTimestamp(6, Timestamp.valueOf(entry.getStartTime()));

                if (entryStmt.executeUpdate() == 0) {
                    throw new SQLException("Fallo al crear entrada de tiempo.");
                }

                try (ResultSet rs = entryStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        entry.setId(rs.getInt(1));
                    } else {
                        throw new SQLException("Fallo al obtener ID generado.");
                    }
                }
            }

            // 2. Insertar Etiquetas (si existen)
            if (tagIds != null && !tagIds.isEmpty()) {
                try (PreparedStatement tagStmt = conn.prepareStatement(insertTagSql)) {
                    for (Integer tagId : tagIds) {
                        tagStmt.setInt(1, entry.getId());
                        tagStmt.setInt(2, tagId);
                        tagStmt.addBatch();
                    }
                    tagStmt.executeBatch();
                }
            }

            conn.commit();
            return entry;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Error crítico en rollback de TimeEntry", ex);
                }
            }
            log.error("Error al guardar TimeEntry y tags", e);
            throw new RuntimeException("Error interno al iniciar el cronómetro.", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("Error al cerrar conexión", e);
                }
            }
        }
    }

    public void stopTimer(Integer entryId, java.time.LocalDateTime endTime) {
        String sql = "UPDATE `time_entry` SET end_time = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(endTime));
            stmt.setInt(2, entryId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            log.error("Error al detener cronómetro ID: {}", entryId, e);
            throw new RuntimeException("Error al detener el cronómetro.", e);
        }
    }
    
    /**
     * Recupera el historial de tiempos. 
     * Si es Manager (OWNER/ADMIN) ve todo, si es MEMBER solo ve lo suyo.
     */
    public List<TimeEntry> findEntries(Integer workspaceId, Integer userId, boolean isManager) {
        String sql = isManager 
                ? "SELECT * FROM `time_entry` WHERE workspace_id = ? ORDER BY created_at DESC"
                : "SELECT * FROM `time_entry` WHERE workspace_id = ? AND user_id = ? ORDER BY created_at DESC";
        
        List<TimeEntry> entries = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, workspaceId);
            if (!isManager) {
                stmt.setInt(2, userId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TimeEntry t = new TimeEntry();
                    t.setId(rs.getInt("id"));
                    t.setWorkspaceId(rs.getInt("workspace_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setProjectId(rs.getObject("project_id", Integer.class));
                    t.setTaskId(rs.getObject("task_id", Integer.class));
                    t.setDescription(rs.getString("description"));
                    t.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
                    
                    Timestamp endTimeStamp = rs.getTimestamp("end_time");
                    if (endTimeStamp != null) {
                        t.setEndTime(endTimeStamp.toLocalDateTime());
                    }
                    
                    t.setTagIds(getTagIdsForEntry(conn, t.getId()));
                    
                    entries.add(t);
                }
            }
        } catch (SQLException e) {
            log.error("Error al listar entradas de tiempo de BD", e);
            throw new RuntimeException("Error de base de datos al obtener el historial.", e);
        }
        return entries;
    }
    
    public TimeEntry createManualEntry(TimeEntry entry, List<Integer> tagIds) {
        String insertEntrySql = "INSERT INTO `time_entry` (workspace_id, user_id, project_id, task_id, description, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String insertTagSql = "INSERT INTO `time_entry_tag` (time_entry_id, tag_id) VALUES (?, ?)";

        Connection conn = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement entryStmt = conn.prepareStatement(insertEntrySql, Statement.RETURN_GENERATED_KEYS)) {
                entryStmt.setInt(1, entry.getWorkspaceId());
                entryStmt.setInt(2, entry.getUserId());
                entryStmt.setObject(3, entry.getProjectId(), Types.INTEGER);
                entryStmt.setObject(4, entry.getTaskId(), Types.INTEGER);
                entryStmt.setString(5, entry.getDescription());
                entryStmt.setTimestamp(6, Timestamp.valueOf(entry.getStartTime()));
                entryStmt.setTimestamp(7, Timestamp.valueOf(entry.getEndTime()));

                if (entryStmt.executeUpdate() == 0) {
                    throw new SQLException("Fallo al crear entrada manual.");
                }

                try (ResultSet rs = entryStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        entry.setId(rs.getInt(1));
                    }
                }
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                try (PreparedStatement tagStmt = conn.prepareStatement(insertTagSql)) {
                    for (Integer tagId : tagIds) {
                        tagStmt.setInt(1, entry.getId());
                        tagStmt.setInt(2, tagId);
                        tagStmt.addBatch();
                    }
                    tagStmt.executeBatch();
                }
            }

            conn.commit();
            return entry;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback fallido", ex); }
            }
            log.error("Error al guardar entrada manual", e);
            throw new RuntimeException("Error interno al registrar tiempo.", e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { log.error("Cierre fallido", e); }
            }
        }
    }
    
    public TimeEntry findById(Integer id) {
        String sql = "SELECT * FROM `time_entry` WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TimeEntry t = new TimeEntry();
                    t.setId(rs.getInt("id"));
                    t.setWorkspaceId(rs.getInt("workspace_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setProjectId(rs.getObject("project_id", Integer.class));
                    t.setTaskId(rs.getObject("task_id", Integer.class));
                    t.setDescription(rs.getString("description"));
                    t.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
                    Timestamp end = rs.getTimestamp("end_time");
                    if (end != null) t.setEndTime(end.toLocalDateTime());
                    
                    t.setTagIds(getTagIdsForEntry(conn, t.getId()));
                    
                    return t;
                }
            }
        } catch (SQLException e) { log.error("Error al buscar TimeEntry", e); }
        return null;
    }

    public TimeEntry updateEntry(TimeEntry entry, List<Integer> tagIds) {
        String updateEntrySql = "UPDATE `time_entry` SET project_id=?, task_id=?, description=?, start_time=?, end_time=? WHERE id=?";
        String deleteTagsSql = "DELETE FROM `time_entry_tag` WHERE time_entry_id=?";
        String insertTagSql = "INSERT INTO `time_entry_tag` (time_entry_id, tag_id) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(updateEntrySql)) {
                stmt.setObject(1, entry.getProjectId(), Types.INTEGER);
                stmt.setObject(2, entry.getTaskId(), Types.INTEGER);
                stmt.setString(3, entry.getDescription());
                stmt.setTimestamp(4, Timestamp.valueOf(entry.getStartTime()));
                stmt.setTimestamp(5, entry.getEndTime() != null ? Timestamp.valueOf(entry.getEndTime()) : null);
                stmt.setInt(6, entry.getId());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(deleteTagsSql)) {
                stmt.setInt(1, entry.getId());
                stmt.executeUpdate();
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                try (PreparedStatement stmt = conn.prepareStatement(insertTagSql)) {
                    for (Integer tagId : tagIds) {
                        stmt.setInt(1, entry.getId());
                        stmt.setInt(2, tagId);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            conn.commit();
            return entry;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback fallido", ex); }
            throw new RuntimeException("Error al actualizar entrada.", e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { log.error("Cierre fallido", e); }
        }
    }

    public void deleteEntry(Integer id) {
        String sql = "DELETE FROM `time_entry` WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar entrada.", e);
        }
    }
    
    private List<Integer> getTagIdsForEntry(Connection conn, Integer entryId) throws SQLException {
    List<Integer> ids = new ArrayList<>();
    String sql = "SELECT tag_id FROM `time_entry_tag` WHERE time_entry_id = ?";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setInt(1, entryId);
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("tag_id"));
            }
        }
    }
    return ids;
}
}
