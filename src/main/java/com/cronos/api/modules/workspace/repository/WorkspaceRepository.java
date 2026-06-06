package com.cronos.api.modules.workspace.repository;

import com.cronos.api.core.DatabaseManager;
import com.cronos.api.modules.workspace.model.Workspace;
import com.cronos.api.modules.workspace.model.WorkspaceMemberResponse;
import com.cronos.api.modules.workspace.model.WorkspaceRole;
import com.cronos.api.modules.workspace.model.WorkspaceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRepository.class);
    private final DatabaseManager dbManager;

    public WorkspaceRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Crea un Workspace y vincula automáticamente a su creador como miembro.
     * Utiliza una transacción SQL para garantizar la integridad referencial.
     */
    public Workspace createWorkspace(Workspace workspace, Integer creatorId) {
        String insertWorkspaceSql = "INSERT INTO `workspace` (name, description, owner_id, status) VALUES (?, ?, ?, ?)";
        String insertMemberSql = "INSERT INTO `workspace_member` (workspace_id, user_id, role, executor_id, executed_by) VALUES (?, ?, ?, ?, 'USER')";

        Connection conn = null;

        try {
            // Obtenemos la conexión del pool
            conn = dbManager.getConnection();
            
            // 1. INICIO DE LA TRANSACCIÓN
            // Desactivamos el auto-commit para que MySQL espere nuestra confirmación final
            conn.setAutoCommit(false);

            // --- Operación A: Insertar el Workspace ---
            try (PreparedStatement wsStmt = conn.prepareStatement(insertWorkspaceSql, Statement.RETURN_GENERATED_KEYS)) {
                wsStmt.setString(1, workspace.getName());
                wsStmt.setString(2, workspace.getDescription());
                wsStmt.setInt(3, creatorId);
                wsStmt.setString(4, workspace.getStatus().name());

                int affectedRows = wsStmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Fallo al crear el workspace, no se insertaron filas.");
                }

                // Recuperar el ID generado para el Workspace
                try (ResultSet generatedKeys = wsStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        workspace.setId(generatedKeys.getInt(1));
                        workspace.setCreatedAt(java.time.LocalDateTime.now());
                    } else {
                        throw new SQLException("Fallo al crear el workspace, no se obtuvo el ID.");
                    }
                }
            }

            // --- Operación B: Insertar al creador como miembro ---
            try (PreparedStatement memberStmt = conn.prepareStatement(insertMemberSql)) {
                memberStmt.setInt(1, workspace.getId());
                memberStmt.setInt(2, creatorId);
                memberStmt.setString(3, WorkspaceRole.OWNER.name());
                memberStmt.setInt(4, creatorId);

                int memberRows = memberStmt.executeUpdate();
                if (memberRows == 0) {
                    throw new SQLException("Fallo al vincular al usuario creador con el workspace.");
                }
            }

            // 2. CONFIRMACIÓN (COMMIT)
            // Si llegamos a esta línea sin excepciones, ambas consultas fueron un éxito
            conn.commit();
            return workspace;

        } catch (SQLException e) {
            // 3. REVERSIÓN (ROLLBACK)
            // Si cualquier cosa falló, deshacemos todo para evitar datos huérfanos
            if (conn != null) {
                try {
                    log.warn("Revirtiendo transacción de creación de Workspace por error: {}", e.getMessage());
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    log.error("¡Error crítico al intentar hacer rollback!", rollbackEx);
                }
            }
            log.error("Error de base de datos al guardar Workspace", e);
            throw new RuntimeException("Error interno al crear el espacio de trabajo.", e);
            
        } finally {
            // 4. LIMPIEZA
            // Siempre debemos restaurar el comportamiento por defecto y cerrar la conexión
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close(); // Devuelve la conexión al pool de HikariCP
                } catch (SQLException closeEx) {
                    log.error("Error al cerrar la conexión", closeEx);
                }
            }
        }
    }
    
    /**
     * Busca todos los espacios de trabajo a los que pertenece un usuario.
     */
    public List<Workspace> findByUserId(Integer userId) {
        // Usamos un INNER JOIN: Traemos el Workspace SOLO SI el usuario existe en workspace_member
        String sql = "SELECT w.* FROM `workspace` w " +
                     "INNER JOIN `workspace_member` wm ON w.id = wm.workspace_id " +
                     "WHERE wm.user_id = ? AND w.status = 'ACTIVE'";

        List<Workspace> workspaces = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Workspace ws = new Workspace();
                    ws.setId(rs.getInt("id"));
                    ws.setName(rs.getString("name"));
                    ws.setDescription(rs.getString("description"));
                    ws.setOwnerId(rs.getInt("owner_id"));
                    ws.setStatus(WorkspaceStatus.valueOf(rs.getString("status")));
                    ws.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    
                    workspaces.add(ws);
                }
            }
        } catch (SQLException e) {
            log.error("Error al buscar workspaces para el usuario ID: {}", userId, e);
            throw new RuntimeException("Error de base de datos al buscar espacios de trabajo.", e);
        }

        return workspaces;
    }
    
    /**
     * Obtiene el rol de un usuario dentro de un workspace específico.
     * Retorna null si el usuario no pertenece a ese workspace.
     */
    public WorkspaceRole getMemberRole(Integer workspaceId, Integer userId) {
        String sql = "SELECT role FROM `workspace_member` WHERE workspace_id = ? AND user_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, workspaceId);
            stmt.setInt(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return WorkspaceRole.valueOf(rs.getString("role"));
                }
            }
        } catch (SQLException e) {
            log.error("Error al consultar el rol del usuario {} en el workspace {}", userId, workspaceId, e);
        }
        return null;
    }

    /**
     * Añade un nuevo miembro a un espacio de trabajo.
     */
    public void addMember(Integer workspaceId, Integer userId, WorkspaceRole role, Integer executorId) {
        String sql = "INSERT INTO `workspace_member` (workspace_id, user_id, role, executor_id, executed_by) VALUES (?, ?, ?, ?, 'USER')";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, workspaceId);
            stmt.setInt(2, userId);
            stmt.setString(3, role.name());
            stmt.setInt(4, executorId);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            log.error("Error al añadir al miembro al workspace", e);
            throw new RuntimeException("No se pudo añadir el miembro a la base de datos.", e);
        }
    }
    
    /**
     * Obtiene la lista de todos los miembros de un espacio de trabajo con sus roles.
     */
    public List<WorkspaceMemberResponse> getWorkspaceMembers(Integer workspaceId) {
        // Asumiendo que tu tabla de usuarios se llama 'user' o 'users'
        String sql = "SELECT u.id, u.username, wm.role " +
                     "FROM `workspace_member` wm " +
                     "INNER JOIN `user` u ON wm.user_id = u.id " +
                     "WHERE wm.workspace_id = ?";

        List<WorkspaceMemberResponse> members = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, workspaceId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(new WorkspaceMemberResponse(
                        rs.getInt("id"),
                        rs.getString("username"),
                        WorkspaceRole.valueOf(rs.getString("role"))
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Error al obtener los miembros del workspace {}", workspaceId, e);
            throw new RuntimeException("Error de base de datos al listar miembros.", e);
        }

        return members;
    }
}
