package com.github.embed.scheduler.dto.taskparams;

import com.github.embed.scheduler.entity.TaskConfig;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * DTO for storing parameters specific to Shell Script tasks.
 * This object will be serialized to/from JSON and stored in the
 * {@link TaskConfig#beanParameters} field
 * when the {@link TaskConfig#taskType} is SHELL_SCRIPT (1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShellTaskParameters {
    /**
     * The script content itself (if {@link #isInlineScript} is true) or the
     * full path to the script file (if {@link #isInlineScript} is false).
     * Paths should be accessible from the server where the scheduler instance is running.
     */
    private String script;

    /**
     * Flag indicating how the {@link #script} field should be interpreted.
     * If {@code true}, the {@link #script} field contains the actual script content to be executed.
     * A temporary file will be created to run this inline script.
     * If {@code false} (default), the {@link #script} field is treated as a path to an existing script file.
     */
    private boolean isInlineScript = false;

    /**
     * A list of arguments to be passed to the shell script or command during execution.
     * Each string in the list will be treated as a separate command-line argument.
     * These arguments are passed after the script itself in the command line.
     */
    private List<String> arguments;

    /**
     * The optional working directory from which the shell script or command should be executed.
     * If null or empty, the default working directory of the scheduler's Java process
     * or a system-dependent temporary directory (for inline scripts) will be used.
     */
    private String workingDirectory;
}
