package com.cronos.api.modules.workspace.controller;

import com.cronos.api.modules.workspace.model.MemberInviteRequest;
import com.cronos.api.modules.workspace.model.WorkspaceCreateRequest;
import com.cronos.api.modules.workspace.model.WorkspaceMemberResponse;
import com.cronos.api.modules.workspace.model.WorkspaceResponse;
import com.cronos.api.modules.workspace.service.WorkspaceService;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * POST /api/workspaces
     */
    public void create(Context ctx) {
        log.info("Recibiendo petición para crear un Workspace...");

        try {
            // Extraemos el DTO del cuerpo de la petición
            WorkspaceCreateRequest request = ctx.bodyAsClass(WorkspaceCreateRequest.class);
            
            // Extraemos el ID del usuario que nuestro AuthMiddleware inyectó en el contexto
            Integer userId = ctx.attribute("userId");

            // Ejecutamos la lógica de negocio
            WorkspaceResponse response = workspaceService.createWorkspace(request, userId);

            ctx.status(201).json(response);
            log.info("Workspace creado exitosamente: ID {}", response.getId());

        } catch (IllegalArgumentException e) {
            ctx.status(400).json(java.util.Map.of("error", "Bad Request", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al crear el Workspace", e);
            ctx.status(500).json(java.util.Map.of("error", "Internal Server Error", "message", "Ocurrió un error al crear el espacio."));
        }
    }
    
    /**
     * GET /api/workspaces
     */
    public void getAll(Context ctx) {
        log.info("Recibiendo petición para listar Workspaces...");

        try {
            // El AuthMiddleware ya validó el token y nos dejó el ID listo para usar
            Integer userId = ctx.attribute("userId");

            List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(userId);

            // Devolvemos 200 OK con el array JSON
            ctx.status(200).json(workspaces);

        } catch (Exception e) {
            log.error("Error al listar los Workspaces", e);
            ctx.status(500).json(Map.of(
                "error", "Internal Server Error",
                "message", "Ocurrió un error al obtener los espacios de trabajo."
            ));
        }
    }
    
    /**
     * POST /api/workspaces/{workspaceId}/members
     */
    public void inviteMember(Context ctx) {
        log.info("Recibiendo petición de invitación a Workspace...");
        try {
            Integer workspaceId = Integer.parseInt(ctx.pathParam("workspaceId"));
            Integer inviterId = ctx.attribute("userId");
            MemberInviteRequest request = ctx.bodyAsClass(MemberInviteRequest.class);

            workspaceService.inviteMember(workspaceId, inviterId, request);
            
            ctx.status(201).json(java.util.Map.of("message", "Miembro añadido exitosamente al equipo."));

        } catch (SecurityException e) {
            // 403 Forbidden para errores de falta de permisos
            log.warn("Acceso denegado al invitar: {}", e.getMessage());
            ctx.status(403).json(java.util.Map.of("error", "Forbidden", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(java.util.Map.of("error", "Bad Request", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al invitar miembro", e);
            ctx.status(500).json(java.util.Map.of("error", "Internal Server Error", "message", "Error al procesar la invitación."));
        }
    }
    
    public void getMembers(Context ctx) {
        Integer workspaceId = Integer.parseInt(ctx.pathParam("workspaceId"));
        Integer requesterId = ctx.attribute("userId"); // Extraído por tu AuthMiddleware

        try {
            List<WorkspaceMemberResponse> members = workspaceService.getWorkspaceMembers(workspaceId, requesterId);
            ctx.json(members);
        } catch (SecurityException e) {
            ctx.status(403).result(e.getMessage());
        } catch (Exception e) {
            log.error("Error al obtener miembros para el workspace {}", workspaceId, e);
            ctx.status(500).result("Error interno del servidor.");
        }
    }
}
