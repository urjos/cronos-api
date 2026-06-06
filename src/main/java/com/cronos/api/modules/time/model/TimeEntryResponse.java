package com.cronos.api.modules.time.model;

import java.time.LocalDateTime;
import java.util.List;

public class TimeEntryResponse {
    private Integer id;
    private Integer workspaceId;
    private Integer userId;
    private Integer projectId;
    private Integer taskId;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<Integer> tagIds;

    public TimeEntryResponse(TimeEntry entry) {
        this.id = entry.getId();
        this.workspaceId = entry.getWorkspaceId();
        this.userId = entry.getUserId();
        this.projectId = entry.getProjectId();
        this.taskId = entry.getTaskId();
        this.description = entry.getDescription();
        this.startTime = entry.getStartTime();
        this.endTime = entry.getEndTime();
        this.tagIds = entry.getTagIds();
    }

    public Integer getId() { return id; }
    public Integer getWorkspaceId() { return workspaceId; }
    
    public Integer getUserId() { return userId; }
    public Integer getProjectId() { return projectId; }
    
    public Integer getTaskId() { return taskId; }
    public String getDescription() { return description; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    
    public List<Integer> getTagIds() { return tagIds; }
}
