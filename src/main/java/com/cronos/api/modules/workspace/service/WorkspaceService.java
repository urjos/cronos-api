package com.cronos.api.modules.workspace.service;

import com.cronos.api.modules.security.model.User;
import com.cronos.api.modules.security.repository.UserRepository;
import com.cronos.api.modules.workspace.model.MemberInviteRequest;
import com.cronos.api.modules.workspace.model.Workspace;
import com.cronos.api.modules.workspace.model.WorkspaceCreateRequest;
import com.cronos.api.modules.workspace.model.WorkspaceMemberResponse;
import com.cronos.api.modules.workspace.model.WorkspaceResponse;
import com.cronos.api.modules.workspace.model.WorkspaceRole;
import com.cronos.api.modules.workspace.model.WorkspaceStatus;
import com.cronos.api.modules.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.stream.Collectors;

public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    
    public WorkspaceService(WorkspaceRepository workspaceRepository, UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
    }

    public WorkspaceResponse createWorkspace(WorkspaceCreateRequest request, Integer creatorId) {
        // Validaciones básicas de negocio
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del espacio de trabajo es obligatorio.");
        }

        // Mapeo inicial
        Workspace newWorkspace = new Workspace();
        newWorkspace.setName(request.getName().trim());
        newWorkspace.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        newWorkspace.setOwnerId(creatorId);
        newWorkspace.setStatus(WorkspaceStatus.ACTIVE);

        // Persistencia transaccional
        Workspace savedWorkspace = workspaceRepository.createWorkspace(newWorkspace, creatorId);

        return new WorkspaceResponse(savedWorkspace);
    }
    
    /**
     * Obtiene todos los espacios de trabajo activos de un usuario.
     */
    public List<WorkspaceResponse> getUserWorkspaces(Integer userId) {
        return workspaceRepository.findByUserId(userId)
                .stream()
                .map(WorkspaceResponse::new)
                .collect(Collectors.toList());
    }
    
    /**
     * Valida permisos e invita a un nuevo miembro al espacio.
     */
    public void inviteMember(Integer workspaceId, Integer inviterId, MemberInviteRequest request) {
        // 1. Autorización: ¿Quien invita tiene el poder para hacerlo?
        WorkspaceRole inviterRole = workspaceRepository.getMemberRole(workspaceId, inviterId);
        
        if (inviterRole == null) {
            throw new SecurityException("No perteneces a este espacio de trabajo.");
        }
        if (inviterRole == WorkspaceRole.MEMBER) {
            throw new SecurityException("No tienes permisos suficientes (MEMBER) para invitar a otras personas.");
        }

        // 2. Validación de destino: ¿El usuario invitado existe?
        User targetUser = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("El usuario '" + request.getUsername() + "' no existe."));

        // 3. Reglas de negocio lógicas
        if (targetUser.getId().equals(inviterId)) {
            throw new IllegalArgumentException("No puedes invitarte a ti mismo.");
        }
        if (workspaceRepository.getMemberRole(workspaceId, targetUser.getId()) != null) {
            throw new IllegalArgumentException("El usuario ya es miembro de este equipo.");
        }

        // 4. Inserción (Si no mandan rol, por defecto es MEMBER)
        WorkspaceRole roleToAssign = request.getRole() != null ? request.getRole() : WorkspaceRole.MEMBER;
        workspaceRepository.addMember(workspaceId, targetUser.getId(), roleToAssign, inviterId);
    }
    
    /**
     * Obtiene la lista de miembros de un espacio de trabajo validando que el solicitante pertenezca al mismo.
     */
    public List<WorkspaceMemberResponse> getWorkspaceMembers(Integer workspaceId, Integer requesterId) {
        WorkspaceRole role = workspaceRepository.getMemberRole(workspaceId, requesterId);
        if (role == null) {
            throw new SecurityException("No perteneces a este espacio de trabajo.");
        }
        return workspaceRepository.getWorkspaceMembers(workspaceId);
    }
}
