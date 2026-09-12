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
 * A control command queued for an agent to consume (ARCHITECTURE.md §5.1 commands).
 */
@Entity
@Table(name = "TB_M26_COMMAND")
public class Command {

    @Id
    @NotBlank
    @Column(name = "COMMAND_ID", length = 22)
    private String commandId;

    @NotBlank
    @Column(name = "AGENT_ID", length = 22, nullable = false)
    private String agentId;

    @Column(name = "PROCESS_ID", length = 22)
    private String processId;

    @Column(name = "COMMAND_TYPE", length = 32)
    private String commandType;

    @Column(name = "COMMAND_STATUS", length = 32)
    private String commandStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "COMMAND_PARAMETERS")
    private Map<String, Object> parameters;

    @Column(name = "PROCESSED_AT")
    private Instant processedAt;

    @Column(name = "FAILED_REASON", columnDefinition = "text")
    private String failedReason;

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
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

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public String getCommandStatus() {
        return commandStatus;
    }

    public void setCommandStatus(String commandStatus) {
        this.commandStatus = commandStatus;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason = failedReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Command other)) {
            return false;
        }
        return commandId != null && commandId.equals(other.commandId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(commandId);
    }
}
