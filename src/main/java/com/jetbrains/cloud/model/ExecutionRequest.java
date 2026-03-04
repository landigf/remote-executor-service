package com.jetbrains.cloud.model;

/**
 * Request payload for submitting a command execution.
 *
 * <p>The {@code script} field is the shell script to run on the remote
 * executor. Resource constraints ({@code cpuCount}, {@code memoryMb})
 * are forwarded to Docker's {@code --cpus} and {@code -m} flags so
 * the executor respects the caller's resource budget.</p>
 */
public class ExecutionRequest {

    private String script;
    private int cpuCount = 1;
    private int memoryMb = 512;
    private String image = "ubuntu:22.04";

    public ExecutionRequest() {}

    public ExecutionRequest(String script, int cpuCount, int memoryMb, String image) {
        this.script = script;
        this.cpuCount = cpuCount;
        this.memoryMb = memoryMb;
        this.image = image;
    }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public int getCpuCount() { return cpuCount; }
    public void setCpuCount(int cpuCount) { this.cpuCount = cpuCount; }

    public int getMemoryMb() { return memoryMb; }
    public void setMemoryMb(int memoryMb) { this.memoryMb = memoryMb; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
