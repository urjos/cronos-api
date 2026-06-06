package com.cronos.api.modules.workspace.model;

public class WorkspaceMemberResponse {
    
    private Integer userId;
    private String username;
    private WorkspaceRole role;
    
    public WorkspaceMemberResponse(Integer userId, String username, WorkspaceRole role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public WorkspaceRole getRole() { return role; }
    public void setRole(WorkspaceRole role) { this.role = role; }
}
