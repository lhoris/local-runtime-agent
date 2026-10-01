package com.lra.agent.process;

import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import java.time.Instant;

/**
 * Mutable bookkeeping for a single managed process. Package-private: owned by
 * {@link DefaultProcessManager} and read by {@link DefaultProcessMonitor}.
 */
class ManagedProcess {

    private final String modelId;
    private ModelProcess definition;
    private Process process;
    private ProcessState state;
    private Integer pid;
    private Instant startTime;
    private int crashCount;

    ManagedProcess(String modelId, ModelProcess definition) {
        this.modelId = modelId;
        this.definition = definition;
        this.state = ProcessState.STOPPED;
    }

    String getModelId() {
        return modelId;
    }

    ModelProcess getDefinition() {
        return definition;
    }

    void setDefinition(ModelProcess definition) {
        this.definition = definition;
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
        if (process != null) {
            return process.isAlive();
        }
        if (pid == null) {
            return false;
        }
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    long uptimeSec() {
        if (startTime == null) {
            return 0L;
        }
        return Math.max(0L, Instant.now().getEpochSecond() - startTime.getEpochSecond());
    }
}
