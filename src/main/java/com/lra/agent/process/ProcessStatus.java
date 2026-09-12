package com.lra.agent.process;

import com.lra.common.enums.ProcessState;
import java.time.LocalDateTime;

/**
 * Point-in-time runtime status of a managed process (ARCHITECTURE.md §4.1).
 * A plain snapshot; persistence to agent_status is the DBSyncManager's concern.
 */
public class ProcessStatus {

    private String modelId;
    private ProcessState state;
    private Integer pid;
    private Float cpuPercent;
    private Integer memoryMb;
    private Long uptimeSec;
    private LocalDateTime lastUpdate;

    public ProcessStatus() {
    }

    public ProcessStatus(String modelId, ProcessState state) {
        this.modelId = modelId;
        this.state = state;
        this.lastUpdate = LocalDateTime.now();
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
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

    public Long getUptimeSec() {
        return uptimeSec;
    }

    public void setUptimeSec(Long uptimeSec) {
        this.uptimeSec = uptimeSec;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
