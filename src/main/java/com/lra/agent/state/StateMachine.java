package com.lra.agent.state;

import com.lra.common.enums.ProcessState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Encodes the valid process lifecycle transitions (ARCHITECTURE.md §7.1).
 *
 * <pre>
 * STOPPED   -> STARTING
 * STARTING  -> RUNNING | STOPPED | CRASHED (timeout)
 * RUNNING   -> STOPPING | CRASHED | DEGRADED | UNHEALTHY
 * STOPPING  -> STOPPED | CRASHED (forceful)
 * CRASHED   -> STARTING (autoRestart) | STOPPED (restart disabled)
 * DEGRADED  -> RUNNING (recovery) | CRASHED | STOPPING
 * UNHEALTHY -> RUNNING (recovery) | CRASHED | STOPPING
 * </pre>
 */
public final class StateMachine {

    private static final Map<ProcessState, Set<ProcessState>> TRANSITIONS =
            new EnumMap<>(ProcessState.class);

    static {
        TRANSITIONS.put(ProcessState.STOPPED, EnumSet.of(ProcessState.STARTING));
        TRANSITIONS.put(ProcessState.STARTING, EnumSet.of(
                ProcessState.RUNNING, ProcessState.STOPPED, ProcessState.CRASHED));
        TRANSITIONS.put(ProcessState.RUNNING, EnumSet.of(
                ProcessState.STOPPING, ProcessState.CRASHED,
                ProcessState.DEGRADED, ProcessState.UNHEALTHY));
        TRANSITIONS.put(ProcessState.STOPPING, EnumSet.of(
                ProcessState.STOPPED, ProcessState.CRASHED));
        TRANSITIONS.put(ProcessState.CRASHED, EnumSet.of(
                ProcessState.STARTING, ProcessState.STOPPED));
        TRANSITIONS.put(ProcessState.DEGRADED, EnumSet.of(
                ProcessState.RUNNING, ProcessState.CRASHED, ProcessState.STOPPING));
        TRANSITIONS.put(ProcessState.UNHEALTHY, EnumSet.of(
                ProcessState.RUNNING, ProcessState.CRASHED, ProcessState.STOPPING));
    }

    private StateMachine() {
    }

    /**
     * Returns true if a transition from {@code from} to {@code to} is permitted.
     */
    public static boolean isValidTransition(ProcessState from, ProcessState to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ProcessState.class)).contains(to);
    }

    /**
     * Returns the set of states reachable in a single transition from {@code from}.
     */
    public static Set<ProcessState> allowedTargets(ProcessState from) {
        return EnumSet.copyOf(
                TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ProcessState.class)));
    }
}
