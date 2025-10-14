package com.github.embed.scheduler.service;

import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.dto.taskparams.ShellTaskParameters;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionState;
import com.alibaba.fastjson.JSON; // Fastjson import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service responsible for executing Shell Script tasks.
 * It parses {@link ShellTaskParameters} from the task configuration,
 * uses {@link ProcessBuilder} to run the script (either inline or from a file path),
 * captures standard output and standard error, handles execution timeouts,
 * and updates the {@link TaskExecuteLog} with the outcome.
 * <p>
 * **Security Note:** Executing arbitrary shell scripts can pose significant security risks.
 * Ensure that only trusted users can define or modify shell tasks, and that script inputs
 * are carefully validated or sanitized if they come from external sources.
 * The execution environment should also be appropriately secured.
 * </p>
 */
@Service
public class ShellTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ShellTaskExecutor.class);
    private static final String TEMP_SCRIPT_PREFIX = "task_script_";
    private static final String DEFAULT_SHELL_EXTENSION = System.getProperty("os.name").toLowerCase().startsWith("windows") ? ".bat" : ".sh";

    // ObjectMapper no longer needed
    // @Autowired
    // private ObjectMapper objectMapper;

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    // Dedicated executor for handling process stream gobblers to prevent blocking.
    private final ExecutorService streamGobblerExecutor = Executors.newCachedThreadPool(
            r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setName("shell-stream-gobbler-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * Executes a shell script task based on its configuration.
     * The {@link TaskConfig#parameters} field is expected to contain a JSON string
     * representing {@link ShellTaskParameters}.
     *
     * @param taskConfig The configuration of the shell task to execute.
     */
    public void execute(TaskConfig taskConfig) {
        TaskExecuteLog logEntry = new TaskExecuteLog();
        logEntry.setTaskId(taskConfig.getTaskId());
        logEntry.setStartTime(new Timestamp(System.currentTimeMillis()));
        logEntry.setStartTime(new Timestamp(System.currentTimeMillis()));
        logEntry.setState(ExecutionState.RUNNING);
        taskExecuteLogDao.save(logEntry);

        ShellTaskParameters params;
        StringBuilder outputBuilder = new StringBuilder();
        StringBuilder errorBuilder = new StringBuilder();
        Path tempScriptPath = null;
        Process process = null;
        boolean timedOut = false;
        int exitCode = -1; // Default to -1 for cases where exit code might not be retrieved

        try {
            if (!StringUtils.hasText(taskConfig.getParameters())) {
                throw new IllegalArgumentException("Shell task parameters (parameters) are missing or empty for Task ID: " + taskConfig.getTaskId());
            }
            // Replace with Fastjson parsing
            params = JSON.parseObject(taskConfig.getParameters(), ShellTaskParameters.class);

            if (!StringUtils.hasText(params.getScript())) {
                throw new IllegalArgumentException("Script content/path is mandatory for shell tasks. Task ID: " + taskConfig.getTaskId());
            }

            List<String> command = new ArrayList<>();
            if (params.isInlineScript()) {
                tempScriptPath = createTempScriptFile(params.getScript());
                command.add(tempScriptPath.toAbsolutePath().toString());
            } else {
                command.add(params.getScript()); // Path to an existing script
            }

            if (params.getArguments() != null) {
                command.addAll(params.getArguments());
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (StringUtils.hasText(params.getWorkingDirectory())) {
                File workingDir = new File(params.getWorkingDirectory());
                if (workingDir.exists() && workingDir.isDirectory()) {
                    processBuilder.directory(workingDir);
                } else {
                    logger.warn("Working directory '{}' for Task ID {} does not exist or is not a directory. Using default.",
                            params.getWorkingDirectory(), taskConfig.getTaskId());
                }
            }

            logger.info("Executing Shell Task ID {}: Command={}, WorkingDir={}",
                    taskConfig.getTaskId(), String.join(" ", command),
                    processBuilder.directory() != null ? processBuilder.directory().getAbsolutePath() : "default");

            process = processBuilder.start();

            Future<?> stdoutFuture = streamGobblerExecutor.submit(new StreamGobbler(process.getInputStream(), outputBuilder::append));
            Future<?> stderrFuture = streamGobblerExecutor.submit(new StreamGobbler(process.getErrorStream(), errorBuilder::append));

            Integer timeoutSeconds = taskConfig.getExecuteTimeoutSeconds();

            if (timeoutSeconds != null && timeoutSeconds > 0) {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    timedOut = true;
                    process.destroyForcibly(); // Ensure subprocess is killed
                    logger.warn("Shell Task ID {} timed out after {} seconds and was forcibly destroyed.", taskConfig.getTaskId(), timeoutSeconds);
                    logEntry.setState(ExecutionState.TIMED_OUT);
                    logEntry.setExMsg("Task execution timed out after " + timeoutSeconds + " seconds.");
                } else {
                    exitCode = process.exitValue();
                }
            } else {
                exitCode = process.waitFor(); // Wait indefinitely if no timeout
            }

            // Wait for stream gobblers to finish reading output, with a safety timeout
            try {
                stdoutFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Stdout gobbler for Task ID {} timed out or was interrupted while finishing. Output may be incomplete.", taskConfig.getTaskId(), e);
            }
            try {
                stderrFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Stderr gobbler for Task ID {} timed out or was interrupted while finishing. Error output may be incomplete.", taskConfig.getTaskId(), e);
            }

            if (!timedOut) {
                String finalStdout = outputBuilder.toString().trim();
                String finalStderr = errorBuilder.toString().trim();

                logEntry.setRtnMsg("Exit Code: " + exitCode + (StringUtils.hasText(finalStdout) ? "\nSTDOUT:\n" + finalStdout : ""));

                if (exitCode == 0) {
                    logEntry.setState(ExecutionState.SUCCESS);
                    logEntry.setExMsg(StringUtils.hasText(finalStderr) ? "STDERR:\n" + finalStderr : null);
                } else {
                    logEntry.setState(ExecutionState.FAILED);
                    logEntry.setExMsg("Exit Code: " + exitCode + (StringUtils.hasText(finalStderr) ? "\nSTDERR:\n" + finalStderr : ""));
                }
                logger.info("Shell Task ID {} finished. Exit Code: {}. STDOUT length: {}, STDERR length: {}",
                        taskConfig.getTaskId(), exitCode, finalStdout.length(), finalStderr.length());
            }

        } catch (IllegalArgumentException e) {
            logger.error("Shell Task ID {} configuration error: {}. Parameters: {}", taskConfig.getTaskId(), e.getMessage(), taskConfig.getParameters(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("Invalid task parameters: " + e.getMessage());
        } catch (com.alibaba.fastjson.JSONException e) { // Fastjson parsing exception
            logger.error("Shell Task ID {} JSON parsing error: {}. Parameters: {}", taskConfig.getTaskId(), e.getMessage(), taskConfig.getParameters(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("Invalid task parameters JSON format: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Shell Task ID {} failed: IO error during execution (e.g., script not found, permission issue). {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Shell Task ID {} execution was interrupted.", taskConfig.getTaskId(), e);
            logEntry.setState(ExecutionState.FAILED); // Or a more specific state like "INTERRUPTED" if desired
            logEntry.setExMsg("Task execution interrupted: " + e.getMessage());
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Exception e) { // Catch-all for other unexpected runtime errors
            logger.error("Shell Task ID {} failed: Unexpected error. {}", taskConfig.getTaskId(), e.getMessage(), e);
            logEntry.setState(ExecutionState.FAILED);
            logEntry.setExMsg("Unexpected error: " + e.getMessage());
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        } finally {
            if (tempScriptPath != null) {
                try {
                    Files.deleteIfExists(tempScriptPath);
                    logger.debug("Deleted temporary script file: {}", tempScriptPath);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary script file: {}. Error: {}", tempScriptPath, e.getMessage());
                }
            }
            // Ensure rtnMsg and exMsg are not excessively long for DB storage
            if (logEntry.getRtnMsg() != null && logEntry.getRtnMsg().length() > 1950) { // Max length for rtn_msg
                logEntry.setRtnMsg(logEntry.getRtnMsg().substring(0, 1950) + "...");
            }
            if (logEntry.getExMsg() != null && logEntry.getExMsg().length() > 1950) { // Max length for ex_msg
                logEntry.setExMsg(logEntry.getExMsg().substring(0, 1950) + "...");
            }
            logEntry.setEndTime(new Timestamp(System.currentTimeMillis()));
            taskExecuteLogDao.update(logEntry);
        }
    }

    /**
     * Creates a temporary script file with the given content.
     * On non-Windows systems, it attempts to set executable permissions.
     *
     * @param scriptContent The content of the script.
     * @return The {@link Path} to the created temporary script file.
     * @throws IOException If an I/O error occurs creating or writing the file.
     */
    private Path createTempScriptFile(String scriptContent) throws IOException {
        Path tempFile = Files.createTempFile(TEMP_SCRIPT_PREFIX, DEFAULT_SHELL_EXTENSION);
        Files.write(tempFile, scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE
                ); // rwxr-xr-x
                Files.setPosixFilePermissions(tempFile, perms);
            } catch (UnsupportedOperationException e) {
                logger.warn("POSIX file permissions not supported on temp file system for {}. Script may need to be called with an interpreter explicitly (e.g., 'sh {}').", tempFile, e);
            } catch (IOException e) {
                logger.error("Failed to set executable permission on temporary script file: {}. Error: {}", tempFile, e.getMessage());
                // Depending on system, execution might still work or might need 'sh /path/to/script'
            }
        }
        logger.debug("Created temporary script file: {}", tempFile.toAbsolutePath());
        return tempFile;
    }

    /**
     * Helper class to consume and buffer an InputStream in a separate thread.
     * This prevents the main thread from blocking if the process generates a lot of output.
     */
    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final Consumer<String> lineConsumer; // Changed to consume line by line

        public StreamGobbler(InputStream inputStream, Consumer<String> lineConsumer) {
            this.inputStream = inputStream;
            this.lineConsumer = lineConsumer;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line + "\n"); // Append newline as readLine() strips it
                }
            } catch (IOException e) {
                // This can happen if the process is destroyed and streams are closed abruptly.
                logger.warn("Error reading stream in StreamGobbler: {}. This might be normal if process was killed.", e.getMessage());
            }
        }
    }
}
