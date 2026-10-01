package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Objects;

/**
 * Latest observed runtime status of a managed model process
 * (ARCHITECTURE.md §5.1 model_process).
 */
@Entity
@Table(name = "TB_M26_MODEL_PROCESS")
public class ModelProcess {

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
        if (!(o instanceof ModelProcess other)) {
            return false;
        }
        return processId != null && processId.equals(other.processId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(processId);
    }
}
