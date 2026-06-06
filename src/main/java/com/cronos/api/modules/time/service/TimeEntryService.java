package com.cronos.api.modules.time.service;

import com.cronos.api.modules.time.model.TimeEntry;
import com.cronos.api.modules.time.model.TimeEntryRequest;
import com.cronos.api.modules.time.model.TimeEntryResponse;
import com.cronos.api.modules.time.model.TimeEntryUpdateRequest;
import com.cronos.api.modules.time.repository.TimeEntryRepository;
import com.cronos.api.modules.workspace.model.WorkspaceRole;
import com.cronos.api.modules.workspace.repository.WorkspaceRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final WorkspaceRepository workspaceRepository;

    public TimeEntryService(TimeEntryRepository timeEntryRepository, WorkspaceRepository workspaceRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public TimeEntryResponse startTimer(Integer workspaceId, Integer userId, TimeEntryRequest request) {
        // 1. Validar pertenencia al Workspace
        WorkspaceRole role = workspaceRepository.getMemberRole(workspaceId, userId);
        if (role == null) {
            throw new SecurityException("No perteneces a este espacio de trabajo.");
        }

        // 2. Validar que no exista un cronómetro activo
        TimeEntry activeEntry = timeEntryRepository.findActiveTimer(workspaceId, userId);
        if (activeEntry != null) {
            throw new IllegalStateException("Ya tienes un cronómetro en curso. Detenlo antes de iniciar otro.");
        }

        // 3. Preparar entidad
        TimeEntry entry = new TimeEntry();
        entry.setWorkspaceId(workspaceId);
        entry.setUserId(userId);
        entry.setProjectId(request.getProjectId());
        entry.setTaskId(request.getTaskId());
        entry.setDescription(request.getDescription() != null ? request.getDescription().trim() : "Sin descripción");
        entry.setStartTime(LocalDateTime.now());
        
        entry.setTagIds(request.getTagIds());

        // 4. Persistir con transacción (incluye tags)
        TimeEntry savedEntry = timeEntryRepository.startTimer(entry, request.getTagIds());

        return new TimeEntryResponse(savedEntry);
    }

    public TimeEntryResponse stopTimer(Integer workspaceId, Integer userId, Integer entryId) {
        // 1. Validar pertenencia al Workspace
        WorkspaceRole role = workspaceRepository.getMemberRole(workspaceId, userId);
        if (role == null) {
            throw new SecurityException("No perteneces a este espacio de trabajo.");
        }

        // 2. Validar que el cronómetro exista y esté activo
        TimeEntry activeEntry = timeEntryRepository.findActiveTimer(workspaceId, userId);
        if (activeEntry == null || !activeEntry.getId().equals(entryId)) {
            throw new IllegalArgumentException("No se encontró este cronómetro en curso.");
        }

        // 3. Detener cronómetro
        LocalDateTime endTime = LocalDateTime.now();
        timeEntryRepository.stopTimer(entryId, endTime);
        
        activeEntry.setEndTime(endTime);
        return new TimeEntryResponse(activeEntry);
    }
    
    /**
     * Obtiene el historial filtrado por permisos de rol.
     */
    public List<TimeEntryResponse> getWorkspaceEntries(Integer workspaceId, Integer userId) {
        WorkspaceRole role = workspaceRepository.getMemberRole(workspaceId, userId);
        if (role == null) {
            throw new SecurityException("No perteneces a este espacio de trabajo.");
        }

        // Determinar si tiene rango para ver los tiempos de todo el equipo
        boolean isManager = (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN);

        return timeEntryRepository.findEntries(workspaceId, userId, isManager)
                .stream()
                .map(TimeEntryResponse::new)
                .collect(java.util.stream.Collectors.toList());
    }
    
    public TimeEntryResponse createManual(Integer workspaceId, Integer userId, com.cronos.api.modules.time.model.TimeEntryManualRequest request) {
        WorkspaceRole role = workspaceRepository.getMemberRole(workspaceId, userId);
        if (role == null) {
            throw new SecurityException("No perteneces a este espacio de trabajo.");
        }

        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias.");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }

        TimeEntry entry = new TimeEntry();
        entry.setWorkspaceId(workspaceId);
        entry.setUserId(userId);
        entry.setProjectId(request.getProjectId());
        entry.setTaskId(request.getTaskId());
        entry.setDescription(request.getDescription() != null ? request.getDescription().trim() : "Entrada manual");
        entry.setStartTime(request.getStartTime());
        entry.setEndTime(request.getEndTime());

        TimeEntry savedEntry = timeEntryRepository.createManualEntry(entry, request.getTagIds());
        return new TimeEntryResponse(savedEntry);
    }
    
    private TimeEntry getAndValidateOwnership(Integer workspaceId, Integer userId, Integer entryId) {
        WorkspaceRole role = workspaceRepository.getMemberRole(workspaceId, userId);
        if (role == null) throw new SecurityException("No perteneces a este espacio de trabajo.");

        TimeEntry entry = timeEntryRepository.findById(entryId);
        if (entry == null || !entry.getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("El registro no existe en este espacio.");
        }

        // Solo OWNER/ADMIN o el propio creador pueden modificar/eliminar
        if (role == WorkspaceRole.MEMBER && !entry.getUserId().equals(userId)) {
            throw new SecurityException("No tienes permisos para modificar este registro.");
        }
        return entry;
    }

    public TimeEntryResponse updateEntry(Integer workspaceId, Integer userId, Integer entryId, TimeEntryUpdateRequest request) {
        TimeEntry entry = getAndValidateOwnership(workspaceId, userId, entryId);

        if (request.getStartTime() == null) throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        if (request.getEndTime() != null && request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }

        entry.setDescription(request.getDescription() != null ? request.getDescription().trim() : entry.getDescription());
        entry.setProjectId(request.getProjectId());
        entry.setTaskId(request.getTaskId());
        entry.setStartTime(request.getStartTime());
        entry.setEndTime(request.getEndTime());

        TimeEntry updatedEntry = timeEntryRepository.updateEntry(entry, request.getTagIds());
        
        updatedEntry.setTagIds(request.getTagIds() != null ? request.getTagIds() : new ArrayList<>());
        
        return new TimeEntryResponse(updatedEntry);
    }

    public void deleteEntry(Integer workspaceId, Integer userId, Integer entryId) {
        getAndValidateOwnership(workspaceId, userId, entryId);
        timeEntryRepository.deleteEntry(entryId);
    }
}
