package com.lra.agent.process;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Seam over {@link ProcessBuilder} so process launching can be substituted in
 * tests. The production implementation is {@link ProcessBuilderLauncher}.
 */
@FunctionalInterface
public interface ProcessLauncher {

    /**
     * Start a native process.
     *
     * @param command          executable followed by its arguments
     * @param workingDirectory working directory, or {@code null} for the current one
     * @param env              environment variables to add to the process environment
     */
    Process launch(List<String> command, File workingDirectory, Map<String, String> env)
        throws IOException;
}
