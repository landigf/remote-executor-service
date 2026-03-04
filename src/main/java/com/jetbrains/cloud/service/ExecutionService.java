package com.jetbrains.cloud.service;

import com.jetbrains.cloud.model.Execution;
import com.jetbrains.cloud.model.ExecutionRequest;
import com.jetbrains.cloud.model.ExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Orchestrates the full lifecycle of a command execution:
 *
 * <ol>
 *   <li>Accept the request, assign an ID, mark QUEUED</li>
 *   <li>Start a Docker container with the requested resource limits</li>
 *   <li>Wait for the container to initialise, mark IN_PROGRESS</li>
 *   <li>Execute the user's script inside the container</li>
 *   <li>Collect output and exit code, mark FINISHED</li>
 *   <li>Tear down the container</li>
 * </ol>
 *
 * <p>All heavy work happens on a background thread pool so the
 * POST endpoint returns immediately with the QUEUED execution.</p>
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final DockerExecutor docker;
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public ExecutionService(DockerExecutor docker) {
        this.docker = docker;
    }

    /**
     * Submits a new execution. Returns immediately with status QUEUED;
     * the actual container work runs asynchronously.
     */
    public Execution submit(ExecutionRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 12);
        Execution execution = new Execution(id, request);
        executions.put(id, execution);

        log.info("Execution {} queued (cpus={}, mem={}MB, image={})",
                id, request.getCpuCount(), request.getMemoryMb(), request.getImage());

        threadPool.submit(() -> runExecution(execution));
        return execution;
    }

    public Execution get(String id) {
        return executions.get(id);
    }

    public List<Execution> listAll() {
        return executions.values().stream()
                .sorted(Comparator.comparingLong(Execution::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // ---- private lifecycle ----

    private void runExecution(Execution exec) {
        String containerId = null;
        try {
            // 1. Start a fresh container with resource constraints.
            containerId = docker.startContainer(
                    exec.getImage(), exec.getCpuCount(), exec.getMemoryMb());
            exec.setContainerId(containerId);
            log.info("Execution {}: container {} started", exec.getId(), shortId(containerId));

            // 2. Wait until the container is running (initialisation phase).
            docker.waitForReady(containerId);

            // 3. Executor is ready — transition to IN_PROGRESS.
            exec.setStatus(ExecutionStatus.IN_PROGRESS);
            exec.setStartedAt(System.currentTimeMillis());
            log.info("Execution {}: IN_PROGRESS", exec.getId());

            // 4. Run the user's script.
            DockerExecutor.ExecResult result = docker.exec(containerId, exec.getScript());

            // 5. Record the result and mark FINISHED.
            exec.setOutput(result.output());
            exec.setExitCode(result.exitCode());
            exec.setStatus(ExecutionStatus.FINISHED);
            exec.setFinishedAt(System.currentTimeMillis());
            log.info("Execution {}: FINISHED (exit={})", exec.getId(), result.exitCode());

        } catch (Exception e) {
            log.error("Execution {} failed: {}", exec.getId(), e.getMessage(), e);
            exec.setStatus(ExecutionStatus.FINISHED);
            exec.setError(e.getMessage());
            exec.setExitCode(-1);
            exec.setFinishedAt(System.currentTimeMillis());
        } finally {
            // 6. Always clean up the container.
            if (containerId != null) {
                docker.removeContainer(containerId);
            }
        }
    }

    private String shortId(String id) {
        return id.length() > 12 ? id.substring(0, 12) : id;
    }
}
