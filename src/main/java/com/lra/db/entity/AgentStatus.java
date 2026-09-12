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
@Table(name = "TB_M26_AGENT_STATUS")
public class AgentStatus {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "STATUS_ID", length = 64)
    private String statusId;

    @NotBlank
    @Column(name = "AGENT_ID", length = 64, nullable = false)
    private String agentId;

    @Column(name = "PROCESS_ID", length = 64)
    private String processId;

    @Column(name = "PROCESS_STATE", length = 32)
    private String state;

    @Column(name = "PROCESS_PID")
    private Integer pid;

    @Column(name = "CPU_PERCENT")
    private Float cpuPercent;

    @Column(name = "MEMORY_MB")
    private Integer memoryMb;

    @Column(name = "LAST_HEALTH_CHECK")
    private Instant lastHealthCheck;

    @Column(name = "LAST_HEARTBEAT")
    private Instant lastHeartbeat;

    @Column(name = "HEALTH_STATUS", length = 32)
    private String healthStatus;

    @Column(name = "UPTIME_SEC")
    private Long uptimeSec;

    @Column(name = "CRASH_COUNT")
    private Integer crashCount;

    @Column(name = "LAST_CRASH_TIME")
    private Instant lastCrashTime;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "text")
    private String errorMessage;

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
