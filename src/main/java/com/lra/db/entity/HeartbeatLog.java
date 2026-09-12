package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A periodic liveness ping from an agent, carrying a status snapshot
 * (ARCHITECTURE.md §5.1 heartbeat_log).
 */
@Entity
@Table(name = "heartbeat_log")
public class HeartbeatLog {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "heartbeat_id", length = 64)
    private String heartbeatId;

    @NotBlank
    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;

    @Column(name = "heartbeat_time")
    private Instant heartbeatTime;

    /** Snapshot of agent/process status at ping time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "agent_status")
    private Map<String, Object> agentStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public String getHeartbeatId() {
        return heartbeatId;
    }

    public void setHeartbeatId(String heartbeatId) {
        this.heartbeatId = heartbeatId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Instant getHeartbeatTime() {
        return heartbeatTime;
    }

    public void setHeartbeatTime(Instant heartbeatTime) {
        this.heartbeatTime = heartbeatTime;
    }

    public Map<String, Object> getAgentStatus() {
        return agentStatus;
    }

    public void setAgentStatus(Map<String, Object> agentStatus) {
        this.agentStatus = agentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeartbeatLog other)) {
            return false;
        }
        return heartbeatId != null && heartbeatId.equals(other.heartbeatId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(heartbeatId);
    }
}
