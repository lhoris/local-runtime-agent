package com.lra.agent.process;

import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drives crash detection and auto-restart over the processes tracked by
 * {@link DefaultProcessManager} (ARCHITECTURE.md §4.1, §7.4). Meant to be invoked
 * once per agent polling cycle.
 */
@Component
public class DefaultProcessMonitor implements ProcessMonitor {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessMonitor.class);

    private final DefaultProcessManager processManager;

    public DefaultProcessMonitor(DefaultProcessManager processManager) {
        this.processManager = processManager;
    }

    @Override
    public void monitor() {
        for (ManagedProcess mp : processManager.managedProcesses()) {
            processManager.refresh(mp);
        }
    }

    @Override
    public void detectUnhealthy() {
        // refresh() transitions a RUNNING/STARTING process that has died to
        // CRASHED and bumps its crash count exactly once.
        for (ManagedProcess mp : processManager.managedProcesses()) {
            processManager.refresh(mp);
        }
    }

    @Override
    public void autoRestart() {
        for (ManagedProcess mp : processManager.managedProcesses()) {
            if (mp.getState() != ProcessState.CRASHED) {
                continue;
            }
            ModelProcess definition = mp.getDefinition();
            boolean enabled = definition != null && Boolean.TRUE.equals(definition.getAutoRestart());
            int maxAttempts = DefaultProcessManager.maxRestartAttempts(definition);

            if (!enabled) {
                log.info("Auto-restart disabled for {}, leaving STOPPED", mp.getModelId());
                processManager.transition(mp, ProcessState.STOPPED);
                continue;
            }
            if (mp.getCrashCount() < maxAttempts) {
                log.warn("Auto-restarting {} (attempt {}/{})",
                    mp.getModelId(), mp.getCrashCount(), maxAttempts);
                // startProcess applies no delay itself; restart_delay_sec is honored
                // by restartProcess. Here we restart immediately after detection.
                processManager.startProcess(mp.getModelId(), definition);
            } else {
                log.error("Process {} reached max restart attempts ({}), giving up",
                    mp.getModelId(), maxAttempts);
                processManager.transition(mp, ProcessState.STOPPED);
            }
        }
    }
}
