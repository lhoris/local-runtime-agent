package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;

import java.sql.Timestamp;
import java.util.Objects;
import com.lra.db.entity.audit.BaseAudit;

/**
 * Latest observed runtime status of a managed model process
 * (ARCHITECTURE.md §5.1 model_process).
 */
@Entity
@Table(name = "TB_M26_MODEL_PROCESS")
public class ModelProcess extends BaseAudit {

    /** Shared vision-monitor database identifier. */
    @Id
    @Column(name = "MODEL_PROCESS_ID")
    private Long modelProcessId;

    /** Compatibility value used by the local process manager and API contract. */
    @Transient
    private String legacyProcessId;

    @NotBlank
    @Column(name = "AGENT_ID", length = 64, nullable = false)
    private String agentId;

    @Column(name = "MODEL_NAME", length = 256)
    private String modelName;

    @Column(name = "PYTHON_PROJECT_PATH", length = 512)
    private String pythonProjectPath;

    /** Python script and arguments used to identify the actual process. */
    @Column(name = "PYTHON_COMMAND", length = 1024)
    private String pythonCommand;

    @Column(name = "AUTO_RESTART")
    private Boolean autoRestart;

    @Column(name = "MAX_RESTART_ATTEMPTS")
    private Integer maxRestartAttempts;

    @Column(name = "RESTART_DELAY_SEC")
    private Integer restartDelaySec;

    @Column(name = "TIMEOUT_SEC")
    private Integer timeoutSec;

    @Column(name = "HEALTH_CHECK_ENABLED")
    private Boolean healthCheckEnabled;

    @Column(name = "HEALTH_CHECK_INTERVAL_SEC")
    private Integer healthCheckIntervalSec;

    @Column(name = "HEALTH_CHECK_ENDPOINT", length = 512)
    private String healthCheckEndpoint;

    // Shared with vision-monitor. Agent writes the latest lifecycle state here.
    @Column(name = "PROCESS_STATUS", length = 32)
    private String state;

    @Column(name = "PROCESS_PID")
    private Integer pid;

    @Column(name = "LAST_HEALTH_CHECK")
    private Timestamp lastHealthCheck;

    @Column(name = "LAST_HEARTBEAT")
    private Timestamp lastHeartbeat;

    @Column(name = "HEALTH_STATUS", length = 32)
    private String healthStatus;

    @Column(name = "UPTIME_SEC")
    private Long uptimeSec;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "text")
    private String errorMessage;

    public Long getModelProcessId() {
        return modelProcessId;
    }

    public void setModelProcessId(Long modelProcessId) {
        this.modelProcessId = modelProcessId;
    }

    public String getProcessId() {
        if (modelProcessId != null) {
            return String.valueOf(modelProcessId);
        }
        return legacyProcessId;
    }

    public void setProcessId(String processId) {
        if (processId == null || processId.isBlank()) {
            this.modelProcessId = null;
            this.legacyProcessId = processId;
            return;
        }
        try {
            this.modelProcessId = Long.valueOf(processId);
            this.legacyProcessId = null;
        } catch (NumberFormatException ignored) {
            this.modelProcessId = null;
            this.legacyProcessId = processId;
        }
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

    public String getPythonProjectPath() {
        return pythonProjectPath;
    }

    public void setPythonProjectPath(String pythonProjectPath) {
        this.pythonProjectPath = pythonProjectPath;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    public void setPythonCommand(String pythonCommand) {
        this.pythonCommand = pythonCommand;
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

    public Timestamp getLastHealthCheck() {
        return lastHealthCheck;
    }

    public void setLastHealthCheck(Timestamp lastHealthCheck) {
        this.lastHealthCheck = lastHealthCheck;
    }

    public Timestamp getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Timestamp lastHeartbeat) {
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
        return modelProcessId != null && modelProcessId.equals(other.modelProcessId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modelProcessId);
    }
}
