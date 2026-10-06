package com.lra.agent.process;

import com.lra.db.entity.ModelProcess;
import java.util.List;

/**
 * Manages the lifecycle of local Python model processes (ARCHITECTURE.md §4.1).
 */
public interface ProcessManager {

    /** Check one database-defined process against the local runtime state. */
    ProcessStatus checkStatus(ModelProcess definition);

    /** Remove local process state that is no longer defined in the database. */
    void reconcileDefinitions(List<ModelProcess> definitions);

    /** Launch the process for {@code modelId} using {@code definition}. */
    void startProcess(String modelId, ModelProcess definition);

    /** Terminate the process for {@code modelId} using the given strategy. */
    void stopProcess(String modelId, StopStrategy strategy);

    /** Gracefully stop then start the process for {@code modelId}. */
    void restartProcess(String modelId);

    /** Current status of {@code modelId}, or {@code null} if it is not managed. */
    ProcessStatus getStatus(String modelId);

    /** Status of every managed process. */
    List<ProcessStatus> getAllStatus();
}
