package com.lra.agent.process;

import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ProcessConfig;
import java.time.Instant;

/**
 * Mutable bookkeeping for a single managed process. Package-private: owned by
 * {@link DefaultProcessManager} and read by {@link DefaultProcessMonitor}.
 */
class ManagedProcess {

    private final String modelId;
    private ProcessConfig config;
    private Process process;
    private ProcessState state;
    private Integer pid;
    private Instant startTime;
    private int crashCount;

    ManagedProcess(String modelId, ProcessConfig config) {
        this.modelId = modelId;
        this.config = config;
        this.state = ProcessState.STOPPED;
    }

    String getModelId() {
        return modelId;
    }

    ProcessConfig getConfig() {
        return config;
    }

    void setConfig(ProcessConfig config) {
        this.config = config;
    }

    Process getProcess() {
        return process;
    }

    void setProcess(Process process) {
        this.process = process;
    }

    ProcessState getState() {
        return state;
    }

    void setState(ProcessState state) {
        this.state = state;
    }

    Integer getPid() {
        return pid;
    }

    void setPid(Integer pid) {
        this.pid = pid;
    }

    Instant getStartTime() {
        return startTime;
    }

    void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    int getCrashCount() {
        return crashCount;
    }

    void incrementCrashCount() {
        this.crashCount++;
    }

    boolean isAlive() {
        return process != null && process.isAlive();
    }

    long uptimeSec() {
        if (startTime == null) {
            return 0L;
        }
        return Math.max(0L, Instant.now().getEpochSecond() - startTime.getEpochSecond());
    }
}
