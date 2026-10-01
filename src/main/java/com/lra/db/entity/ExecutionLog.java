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
@Table(name = "TB_M26_EXECUTION_LOG")
public class ExecutionLog {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "LOG_ID", length = 64)
    private String logId;

    @Column(name = "AGENT_ID", length = 64)
    private String agentId;

    @Column(name = "PROCESS_ID", length = 64)
    private String processId;

    @Column(name = "COMMAND_TYPE", length = 32)
    private String commandType;

    @Column(name = "EXECUTION_STATUS", length = 32)
    private String executionStatus;

    @Column(name = "EXIT_CODE")
    private Integer exitCode;

    @Column(name = "STDOUT_PREVIEW", columnDefinition = "text")
    private String stdoutPreview;

    @Column(name = "STDERR_PREVIEW", columnDefinition = "text")
    private String stderrPreview;

    @Column(name = "DURATION_SEC")
    private Integer durationSec;

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
