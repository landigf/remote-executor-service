package com.jetbrains.cloud.model;

/**
 * Represents a single command execution and tracks its full lifecycle.
 *
 * <p>Instances are mutable and updated in-place by the
 * {@link com.jetbrains.cloud.service.ExecutionService} as the execution
 * progresses through QUEUED → IN_PROGRESS → FINISHED.</p>
 */
public class Execution {

    private String id;
    private String script;
    private int cpuCount;
    private int memoryMb;
    private String image;
    private volatile ExecutionStatus status;
    private String output;
    private Integer exitCode;
    private String error;
    private long createdAt;
    private Long startedAt;
    private Long finishedAt;
    private String containerId;

    public Execution() {}

    public Execution(String id, ExecutionRequest request) {
        this.id = id;
        this.script = request.getScript();
        this.cpuCount = request.getCpuCount();
        this.memoryMb = request.getMemoryMb();
        this.image = request.getImage();
        this.status = ExecutionStatus.QUEUED;
        this.createdAt = System.currentTimeMillis();
    }

    // --- getters and setters ---

    public String getId() { return id; }

    public String getScript() { return script; }

    public int getCpuCount() { return cpuCount; }

    public int getMemoryMb() { return memoryMb; }

    public String getImage() { return image; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getCreatedAt() { return createdAt; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Long finishedAt) { this.finishedAt = finishedAt; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
}
