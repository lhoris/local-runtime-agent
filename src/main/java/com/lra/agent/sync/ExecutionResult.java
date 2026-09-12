package com.lra.agent.sync;

import com.lra.common.enums.CommandType;

/**
 * Outcome of executing a queued command, ready to be persisted to execution_log
 * (ARCHITECTURE.md §7.3).
 */
public class ExecutionResult {

    private final String commandId;
    private final String modelId;
    private final CommandType commandType;
    private final ExecutionStatus status;
    private final Integer exitCode;
    private final String stdoutPreview;
    private final String stderrPreview;
    private final Integer durationSec;

    public ExecutionResult(String commandId,
                           String modelId,
                           CommandType commandType,
                           ExecutionStatus status,
                           Integer exitCode,
                           String stdoutPreview,
                           String stderrPreview,
                           Integer durationSec) {
        this.commandId = commandId;
        this.modelId = modelId;
        this.commandType = commandType;
        this.status = status;
        this.exitCode = exitCode;
        this.stdoutPreview = stdoutPreview;
        this.stderrPreview = stderrPreview;
        this.durationSec = durationSec;
    }

    public static ExecutionResult success(String commandId,
                                          String modelId,
                                          CommandType commandType,
                                          int durationSec) {
        return new ExecutionResult(commandId, modelId, commandType,
                ExecutionStatus.SUCCESS, 0, null, null, durationSec);
    }

    public static ExecutionResult failure(String commandId,
                                          String modelId,
                                          CommandType commandType,
                                          String errorMessage,
                                          int durationSec) {
        return new ExecutionResult(commandId, modelId, commandType,
                ExecutionStatus.FAILURE, null, null, errorMessage, durationSec);
    }

    public String getCommandId() {
        return commandId;
    }

    public String getModelId() {
        return modelId;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getStdoutPreview() {
        return stdoutPreview;
    }

    public String getStderrPreview() {
        return stderrPreview;
    }

    public Integer getDurationSec() {
        return durationSec;
    }
}
