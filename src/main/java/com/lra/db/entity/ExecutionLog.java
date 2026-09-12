package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * A historical record of a command execution and its outcome
 * (ARCHITECTURE.md §5.1 execution_log).
 */
@Entity
@Table(name = "execution_log")
public class ExecutionLog {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "log_id", length = 64)
    private String logId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "process_id", length = 64)
    private String processId;

    @Column(name = "command_type", length = 32)
    private String commandType;

    @Column(name = "execution_status", length = 32)
    private String executionStatus;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "stdout_preview", columnDefinition = "text")
    private String stdoutPreview;

    @Column(name = "stderr_preview", columnDefinition = "text")
    private String stderrPreview;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
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

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdoutPreview() {
        return stdoutPreview;
    }

    public void setStdoutPreview(String stdoutPreview) {
        this.stdoutPreview = stdoutPreview;
    }

    public String getStderrPreview() {
        return stderrPreview;
    }

    public void setStderrPreview(String stderrPreview) {
        this.stderrPreview = stderrPreview;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
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
        if (!(o instanceof ExecutionLog other)) {
            return false;
        }
        return logId != null && logId.equals(other.logId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(logId);
    }
}
