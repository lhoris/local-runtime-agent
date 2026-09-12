package com.lra.agent.state;

import com.lra.common.enums.ProcessState;

/**
 * Listener notified whenever a managed process changes state.
 */
@FunctionalInterface
public interface StateChangeListener {

    void onStateChange(String modelId, ProcessState oldState, ProcessState newState);
}
