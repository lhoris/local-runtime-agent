package com.lra.agent.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessManagerTest {

    private ProcessLauncher launcher;
    private DefaultProcessManager manager;
    private DefaultProcessMonitor monitor;

    @BeforeEach
    void setUp() {
        launcher = mock(ProcessLauncher.class);
        manager = new DefaultProcessManager(launcher, new ObjectMapper());
        monitor = new DefaultProcessMonitor(manager);
    }

    private ModelProcess config(boolean autoRestart, int maxAttempts) {
        ModelProcess c = new ModelProcess();
        c.setProcessId("m1");
        c.setAgentId("agent-1");
        c.setExecutablePath("python");
        c.setCommandArgs("[\"-u\",\"model.py\"]");
        c.setEnvVars("{\"CUDA_VISIBLE_DEVICES\":\"0\"}");
        c.setAutoRestart(autoRestart);
        c.setMaxRestartAttempts(maxAttempts);
        c.setRestartDelaySec(0);
        return c;
    }

    private Process aliveProcess(long pid) throws Exception {
        Process p = mock(Process.class);
        when(p.pid()).thenReturn(pid);
        when(p.isAlive()).thenReturn(true);
        when(p.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        return p;
    }

    @Test
    void startProcess_setsRunningAndPid() throws Exception {
        Process proc = aliveProcess(1234L);
        when(launcher.launch(any(), any(), any())).thenReturn(proc);

        manager.startProcess("m1", config(true, 3));

        ProcessStatus status = manager.getStatus("m1");
        assertThat(status.getState()).isEqualTo(ProcessState.RUNNING);
        assertThat(status.getPid()).isEqualTo(1234);
    }

    @Test
    void startProcess_whenProcessExitsImmediately_isCrashed() throws Exception {
        Process proc = mock(Process.class);
        when(proc.pid()).thenReturn(9L);
        when(proc.isAlive()).thenReturn(false);
        when(proc.exitValue()).thenReturn(1);
        when(launcher.launch(any(), any(), any())).thenReturn(proc);

        manager.startProcess("m1", config(true, 3));

        assertThat(manager.getStatus("m1").getState()).isEqualTo(ProcessState.CRASHED);
    }

    @Test
    void stopProcess_graceful_destroysAndStops() throws Exception {
        Process proc = aliveProcess(1234L);
        when(launcher.launch(any(), any(), any())).thenReturn(proc);
        manager.startProcess("m1", config(true, 3));

        manager.stopProcess("m1", StopStrategy.GRACEFUL);

        verify(proc).destroy();
        verify(proc, never()).destroyForcibly();
        assertThat(manager.getStatus("m1").getState()).isEqualTo(ProcessState.STOPPED);
    }

    @Test
    void stopProcess_forceful_killsImmediately() throws Exception {
        Process proc = aliveProcess(1234L);
        when(launcher.launch(any(), any(), any())).thenReturn(proc);
        manager.startProcess("m1", config(true, 3));

        manager.stopProcess("m1", StopStrategy.FORCEFUL);

        verify(proc).destroyForcibly();
        verify(proc, never()).destroy();
        assertThat(manager.getStatus("m1").getState()).isEqualTo(ProcessState.STOPPED);
    }

    @Test
    void getStatus_unknownModel_returnsNull() {
        assertThat(manager.getStatus("nope")).isNull();
    }

    @Test
    void detectUnhealthy_marksCrashedAndCountsOnce() throws Exception {
        Process proc = mock(Process.class);
        when(proc.pid()).thenReturn(1234L);
        // alive at start, dead afterwards
        when(proc.isAlive()).thenReturn(true, false);
        when(launcher.launch(any(), any(), any())).thenReturn(proc);
        manager.startProcess("m1", config(true, 3));

        monitor.detectUnhealthy();
        monitor.detectUnhealthy(); // idempotent

        assertThat(manager.getStatus("m1").getState()).isEqualTo(ProcessState.CRASHED);
    }

    @Test
    void autoRestart_relaunchesCrashedProcess() throws Exception {
        Process crashed = mock(Process.class);
        when(crashed.pid()).thenReturn(100L);
        when(crashed.isAlive()).thenReturn(true, false);
        Process restarted = aliveProcess(200L);
        when(launcher.launch(any(), any(), any())).thenReturn(crashed, restarted);

        manager.startProcess("m1", config(true, 3));
        monitor.detectUnhealthy();
        monitor.autoRestart();

        verify(launcher, times(2)).launch(any(), any(), any());
        ProcessStatus status = manager.getStatus("m1");
        assertThat(status.getState()).isEqualTo(ProcessState.RUNNING);
        assertThat(status.getPid()).isEqualTo(200);
    }

    @Test
    void autoRestart_givesUpAfterMaxAttempts() throws Exception {
        Process crashed = mock(Process.class);
        when(crashed.pid()).thenReturn(100L);
        when(crashed.isAlive()).thenReturn(true, false);
        when(launcher.launch(any(), any(), any())).thenReturn(crashed);

        manager.startProcess("m1", config(true, 1)); // maxAttempts = 1
        monitor.detectUnhealthy(); // crashCount becomes 1
        monitor.autoRestart();     // 1 < 1 is false -> give up

        verify(launcher, times(1)).launch(any(), any(), any());
        assertThat(manager.getStatus("m1").getState()).isEqualTo(ProcessState.STOPPED);
    }

    @Test
    void autoRestart_disabled_transitionsToStopped() throws Exception {
        Process proc = mock(Process.class);
        when(proc.pid()).thenReturn(100L);
        when(proc.isAlive()).thenReturn(true, false);
        when(launcher.launch(any(), any(), any())).thenReturn(proc);

        manager.startProcess("m1", config(false, 3)); // auto-restart off
        monitor.detectUnhealthy();
        monitor.autoRestart();

        verify(launcher, times(1)).launch(any(), any(), any());
        assertThat(manager.getStatus("m1").getState()).isEqualTo(ProcessState.STOPPED);
    }
}
