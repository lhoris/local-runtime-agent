package com.lra.agent.process;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Default {@link ProcessLauncher} backed by {@link ProcessBuilder}. Arguments are
 * passed as a list (never a shell string) to avoid command injection
 * (ARCHITECTURE.md §10.2).
 */
@Component
public class ProcessBuilderLauncher implements ProcessLauncher {

    @Override
    public Process launch(List<String> command, File workingDirectory, Map<String, String> env)
        throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null) {
            pb.directory(workingDirectory);
        }
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        pb.redirectErrorStream(false);
        return pb.start();
    }
}
