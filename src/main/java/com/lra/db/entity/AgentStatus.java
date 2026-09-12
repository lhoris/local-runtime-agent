package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Latest observed runtime status of a managed process
 * (ARCHITECTURE.md §5.1 agent_status).
 */
@Entity
@Table(name = "agent_status")
public class AgentStatus {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "status_id", length = 64)
    private String statusId;

    @NotBlank
    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;

    @Column(name = "process_id", length = 64)
    private String processId;

    @Column(name = "state", length = 32)
    private String state;

    @Column(name = "pid")
    private Integer pid;

    @Column(name = "cpu_percent")
    private Float cpuPercent;

    @Column(name = "memory_mb")
    private Integer memoryMb;

    @Column(name = "last_health_check")
    private Instant lastHealthCheck;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "health_status", length = 32)
    private String healthStatus;

    @Column(name = "uptime_sec")
    private Long uptimeSec;

    @Column(name = "crash_count")
    private Integer crashCount;

    @Column(name = "last_crash_time")
    private Instant lastCrashTime;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getStatusId() {
        return statusId;
    }

    public void setStatusId(String statusId) {
        this.statusId = statusId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public Float getCpuPercent() {
        return cpuPercent;
    }

    public void setCpuPercent(Float cpuPercent) {
        this.cpuPercent = cpuPercent;
    }

    public Integer getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(Integer memoryMb) {
        this.memoryMb = memoryMb;
    }

    public Instant getLastHealthCheck() {
        return lastHealthCheck;
    }

    public void setLastHealthCheck(Instant lastHealthCheck) {
        this.lastHealthCheck = lastHealthCheck;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public Long getUptimeSec() {
        return uptimeSec;
    }

    public void setUptimeSec(Long uptimeSec) {
        this.uptimeSec = uptimeSec;
    }

    public Integer getCrashCount() {
        return crashCount;
    }

    public void setCrashCount(Integer crashCount) {
        this.crashCount = crashCount;
    }

    public Instant getLastCrashTime() {
        return lastCrashTime;
    }

    public void setLastCrashTime(Instant lastCrashTime) {
        this.lastCrashTime = lastCrashTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentStatus other)) {
            return false;
        }
        return statusId != null && statusId.equals(other.statusId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(statusId);
    }
}
