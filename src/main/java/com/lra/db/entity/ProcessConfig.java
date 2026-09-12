package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Desired configuration for a Python model process managed by an agent
 * (ARCHITECTURE.md §5.1 process_config).
 */
@Entity
@Table(name = "process_config")
public class ProcessConfig {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "process_id", length = 64)
    private String processId;

    @NotBlank
    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;

    @Column(name = "model_name", length = 256)
    private String modelName;

    @Column(name = "model_type", length = 64)
    private String modelType;

    @Column(name = "executable_path", length = 512)
    private String executablePath;

    @Column(name = "working_directory", length = 512)
    private String workingDirectory;

    /** JSON array of launch arguments, stored as TEXT. */
    @Column(name = "command_args", columnDefinition = "text")
    private String commandArgs;

    /** JSON object of environment variables, stored as TEXT. */
    @Column(name = "env_vars", columnDefinition = "text")
    private String envVars;

    @Column(name = "auto_restart")
    private Boolean autoRestart;

    @Column(name = "max_restart_attempts")
    private Integer maxRestartAttempts;

    @Column(name = "restart_delay_sec")
    private Integer restartDelaySec;

    @Column(name = "timeout_sec")
    private Integer timeoutSec;

    @Column(name = "memory_limit_mb")
    private Integer memoryLimitMb;

    @Column(name = "cpu_limit_percent")
    private Integer cpuLimitPercent;

    @Column(name = "health_check_enabled")
    private Boolean healthCheckEnabled;

    @Column(name = "health_check_interval_sec")
    private Integer healthCheckIntervalSec;

    @Column(name = "health_check_endpoint", length = 512)
    private String healthCheckEndpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getCommandArgs() {
        return commandArgs;
    }

    public void setCommandArgs(String commandArgs) {
        this.commandArgs = commandArgs;
    }

    public String getEnvVars() {
        return envVars;
    }

    public void setEnvVars(String envVars) {
        this.envVars = envVars;
    }

    public Boolean getAutoRestart() {
        return autoRestart;
    }

    public void setAutoRestart(Boolean autoRestart) {
        this.autoRestart = autoRestart;
    }

    public Integer getMaxRestartAttempts() {
        return maxRestartAttempts;
    }

    public void setMaxRestartAttempts(Integer maxRestartAttempts) {
        this.maxRestartAttempts = maxRestartAttempts;
    }

    public Integer getRestartDelaySec() {
        return restartDelaySec;
    }

    public void setRestartDelaySec(Integer restartDelaySec) {
        this.restartDelaySec = restartDelaySec;
    }

    public Integer getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(Integer timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public Integer getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public void setMemoryLimitMb(Integer memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }

    public Integer getCpuLimitPercent() {
        return cpuLimitPercent;
    }

    public void setCpuLimitPercent(Integer cpuLimitPercent) {
        this.cpuLimitPercent = cpuLimitPercent;
    }

    public Boolean getHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(Boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public Integer getHealthCheckIntervalSec() {
        return healthCheckIntervalSec;
    }

    public void setHealthCheckIntervalSec(Integer healthCheckIntervalSec) {
        this.healthCheckIntervalSec = healthCheckIntervalSec;
    }

    public String getHealthCheckEndpoint() {
        return healthCheckEndpoint;
    }

    public void setHealthCheckEndpoint(String healthCheckEndpoint) {
        this.healthCheckEndpoint = healthCheckEndpoint;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
        if (!(o instanceof ProcessConfig other)) {
            return false;
        }
        return processId != null && processId.equals(other.processId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(processId);
    }
}
