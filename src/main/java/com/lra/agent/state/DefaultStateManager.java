package com.lra.agent.state;

import com.lra.common.enums.ProcessState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default in-memory {@link StateManager}. State is held per model and guarded so
 * that concurrent transitions do not corrupt the recorded state.
 */
@Component
public class DefaultStateManager implements StateManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultStateManager.class);

    private final Map<String, ProcessState> states = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public ProcessState getCurrentState(String modelId) {
        return states.getOrDefault(modelId, ProcessState.STOPPED);
    }

    @Override
    public void transitionTo(String modelId, ProcessState newState)
            throws InvalidStateTransitionException {
        ProcessState oldState = getCurrentState(modelId);
        validateTransition(oldState, newState);

        // Guard against a concurrent transition that changed the state between the
        // read above and the write below: only commit if the state is unchanged.
        boolean committed = (oldState == ProcessState.STOPPED && !states.containsKey(modelId))
                ? states.putIfAbsent(modelId, newState) == null
                : states.replace(modelId, oldState, newState);

        if (!committed) {
            ProcessState current = getCurrentState(modelId);
            throw new InvalidStateTransitionException(current, newState);
        }

        logger.info("State transition: {} {} -> {}", modelId, oldState, newState);
        listeners.forEach(l -> notifyListener(l, modelId, oldState, newState));
    }

    @Override
    public void validateTransition(ProcessState from, ProcessState to)
            throws InvalidStateTransitionException {
        if (!StateMachine.isValidTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }

    @Override
    public void addEventListener(StateChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void notifyListener(StateChangeListener listener, String modelId,
                                ProcessState oldState, ProcessState newState) {
        try {
            listener.onStateChange(modelId, oldState, newState);
        } catch (RuntimeException ex) {
            logger.warn("State change listener failed for {} ({} -> {})",
                    modelId, oldState, newState, ex);
        }
    }
}
