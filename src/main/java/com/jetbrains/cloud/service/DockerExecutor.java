package com.jetbrains.cloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Low-level wrapper around the Docker CLI.
 *
 * <p>This class isolates all Docker interactions so the rest of the
 * service doesn't deal with process management directly. In a
 * production system, this could be replaced with an AWS SDK client
 * (EC2 RunInstances / ECS RunTask) or a Kubernetes client (create Job)
 * without touching the rest of the codebase.</p>
 */
@Component
public class DockerExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerExecutor.class);

    /**
     * Starts a detached container with the given resource constraints.
     * The container runs {@code sleep 3600} to stay alive while we
     * exec commands into it.
     *
     * @return the container ID
     */
    public String startContainer(String image, int cpuCount, int memoryMb) throws Exception {
        List<String> cmd = List.of(
                "docker", "run", "-d",
                "--cpus", String.valueOf(cpuCount),
                "-m", memoryMb + "m",
                image,
                "sleep", "3600"
        );
        log.debug("Starting container: {}", String.join(" ", cmd));

        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output = readAll(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("docker run failed (exit " + exitCode + "): " + output);
        }
        return output.trim();
    }

    /**
     * Blocks until the container reaches the "running" state, polling
     * every 500ms. Times out after 15 seconds.
     */
    public void waitForReady(String containerId) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            Process p = new ProcessBuilder(
                    "docker", "inspect", "-f", "{{.State.Running}}", containerId
            ).redirectErrorStream(true).start();

            String status = readAll(p).trim();
            p.waitFor();

            if ("true".equals(status)) {
                log.debug("Container {} is running", shortId(containerId));
                return;
            }
            Thread.sleep(500);
        }
        throw new RuntimeException("Container " + shortId(containerId) + " did not start within 15s");
    }

    /**
     * Executes a shell script inside a running container via
     * {@code docker exec}. The script is passed to {@code /bin/sh -c}
     * so pipes, redirects, and multi-line scripts all work.
     */
    public ExecResult exec(String containerId, String script) throws Exception {
        Process process = new ProcessBuilder(
                "docker", "exec", containerId,
                "/bin/sh", "-c", script
        ).redirectErrorStream(true).start();

        String output = readAll(process);
        int exitCode = process.waitFor();
        return new ExecResult(output, exitCode);
    }

    /**
     * Force-removes a container. Called in a finally block, so it
     * swallows exceptions to avoid masking the original error.
     */
    public void removeContainer(String containerId) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerId)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            log.info("Removed container {}", shortId(containerId));
        } catch (Exception e) {
            log.warn("Failed to remove container {}: {}", shortId(containerId), e.getMessage());
        }
    }

    private String readAll(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private String shortId(String containerId) {
        return containerId.length() > 12 ? containerId.substring(0, 12) : containerId;
    }

    public record ExecResult(String output, int exitCode) {}
}
