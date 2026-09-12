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
@Table(name = "TB_M26_PROCESS_CONFIG")
public class ProcessConfig {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "PROCESS_ID", length = 64)
    private String processId;

    @NotBlank
    @Column(name = "AGENT_ID", length = 64, nullable = false)
    private String agentId;

    @Column(name = "MODEL_NAME", length = 256)
    private String modelName;

    @Column(name = "MODEL_TYPE", length = 64)
    private String modelType;

    @Column(name = "EXECUTABLE_PATH", length = 512)
    private String executablePath;

    @Column(name = "WORKING_DIRECTORY", length = 512)
    private String workingDirectory;

    @Column(name = "COMMAND_ARGS", columnDefinition = "text")
    private String commandArgs;

    @Column(name = "ENV_VARS", columnDefinition = "text")
    private String envVars;

    @Column(name = "AUTO_RESTART")
    private Boolean autoRestart;

    @Column(name = "MAX_RESTART_ATTEMPTS")
    private Integer maxRestartAttempts;

    @Column(name = "RESTART_DELAY_SEC")
    private Integer restartDelaySec;

    @Column(name = "TIMEOUT_SEC")
    private Integer timeoutSec;

    @Column(name = "MEMORY_LIMIT_MB")
    private Integer memoryLimitMb;

    @Column(name = "CPU_LIMIT_PERCENT")
    private Integer cpuLimitPercent;

    @Column(name = "HEALTH_CHECK_ENABLED")
    private Boolean healthCheckEnabled;

    @Column(name = "HEALTH_CHECK_INTERVAL_SEC")
    private Integer healthCheckIntervalSec;

    @Column(name = "HEALTH_CHECK_ENDPOINT", length = 512)
    private String healthCheckEndpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "METADATA")
    private Map<String, Object> metadata;


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
