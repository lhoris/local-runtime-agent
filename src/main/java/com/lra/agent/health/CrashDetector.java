package com.lra.agent.health;

import com.lra.agent.process.ProcessStatus;
import com.lra.common.enums.ProcessState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides whether a process that is believed to be RUNNING has actually
 * crashed, using OS-level PID liveness and zombie detection (ARCHITECTURE.md §7.4).
 */
@Component
public class CrashDetector {

    private static final Logger log = LoggerFactory.getLogger(CrashDetector.class);

    /**
     * @return {@code true} if the process is expected to be alive but is gone
     *         or a zombie. Only meaningful when the tracked state is RUNNING;
     *         any error while probing is treated as "not crashed" to avoid
     *         false positives.
     */
    public boolean isCrashed(String modelId, ProcessStatus status) {
        if (status == null || status.getState() != ProcessState.RUNNING) {
            return false;
        }

        Integer pid = status.getPid();
        if (pid == null) {
            // RUNNING but no PID recorded: cannot verify, do not flag as crashed.
            return false;
        }

        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) {
                return true;
            }
            return isZombie(pid);
        } catch (Exception e) {
            log.warn("Failed to detect crash for {} (pid={})", modelId, pid, e);
            return false;
        }
    }

    /**
     * Detects a zombie/defunct process. On Linux this reads {@code /proc/[pid]/stat}
     * and checks for state {@code Z}. On other platforms (Windows) there is no
     * zombie concept, so this returns {@code false}.
     */
    boolean isZombie(long pid) {
        Path statPath = Path.of("/proc", Long.toString(pid), "stat");
        if (!Files.exists(statPath)) {
            return false;
        }
        try {
            String stat = Files.readString(statPath);
            // Format: "pid (comm) state ...". comm may contain spaces/parens,
            // so parse the state from just after the last ')'.
            int close = stat.lastIndexOf(')');
            if (close < 0 || close + 2 >= stat.length()) {
                return false;
            }
            char state = stat.charAt(close + 2);
            return state == 'Z';
        } catch (IOException e) {
            log.warn("Failed to read process stat for pid={}", pid, e);
            return false;
        }
    }
}
