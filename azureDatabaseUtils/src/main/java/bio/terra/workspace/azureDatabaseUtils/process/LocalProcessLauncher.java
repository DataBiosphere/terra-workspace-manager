package bio.terra.workspace.azureDatabaseUtils.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** This class provides utility methods for launching local child processes. */
public class LocalProcessLauncher {
    private Process process;

    public enum Output {
        OUT,
        ERROR
    }

    /**
     * Executes a command in a separate process from the current working directory (i.e. the same
     * place as this Java process is running).
     *
     * @param command the command and arguments to execute
     * @param envVars the environment variables to set or overwrite if already defined
     */
    public void launchProcess(List<String> command, Map<String, String> envVars) {
        launchProcess(command, envVars, null);
    }

    /**
     * Executes a command in a separate process from the given working directory, with the given
     * environment variables set beforehand.
     *
     * @param command the command and arguments to execute
     * @param envVars the environment variables to set or overwrite if already defined
     * @param workingDirectory the working directory to launch the process from
     */
    public void launchProcess(
            List<String> command, Map<String, String> envVars, Path workingDirectory) {
        // build and run process from the specified working directory
        ProcessBuilder procBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            procBuilder.directory(workingDirectory.toFile());
        }
        if (envVars != null) {
            Map<String, String> procEnvVars = procBuilder.environment();
            procEnvVars.putAll(envVars);
        }

        try {
            process = procBuilder.start();
        } catch (IOException ioEx) {
            throw new LaunchProcessException("Error launching local process", ioEx);
        }
    }

    /**
     * Stream standard out/err from the child process to the CLI console.
     *
     * @param type specifies which process stream to get data from
     */
    public InputStream getOutputForProcess(Output type) {
        if (type == Output.ERROR) {
            return process.getErrorStream();
        }

        return process.getInputStream();
    }

    /** Block until the child process terminates, then return its exit code. */
    public int waitForTerminate() {
        try {
            return process.waitFor();
        } catch (InterruptedException intEx) {
            Thread.currentThread().interrupt();
            throw new LaunchProcessException("Error waiting for child process to terminate", intEx);
        }
    }

    /** Get stdout input stream from the child process. */
    public InputStream getInputStream() {
        return process.getInputStream();
    }

    /** Get stdin output stream from the child process. */
    public OutputStream getOutputStream() {
        return process.getOutputStream();
    }
}
