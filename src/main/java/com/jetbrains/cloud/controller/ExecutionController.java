package com.jetbrains.cloud.controller;

import com.jetbrains.cloud.model.Execution;
import com.jetbrains.cloud.model.ExecutionRequest;
import com.jetbrains.cloud.service.ExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Submit a new command for execution on a remote executor.
     *
     * <p>Returns immediately with status QUEUED. The caller should poll
     * {@code GET /api/executions/{id}} to track progress.</p>
     */
    @PostMapping
    public ResponseEntity<Execution> submit(@RequestBody ExecutionRequest request) {
        if (request.getScript() == null || request.getScript().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(null);
        }
        if (request.getCpuCount() < 1 || request.getCpuCount() > 16) {
            return ResponseEntity.badRequest().body(null);
        }
        if (request.getMemoryMb() < 64 || request.getMemoryMb() > 32768) {
            return ResponseEntity.badRequest().body(null);
        }

        Execution exec = executionService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(exec);
    }

    /**
     * Get the current status and result of an execution.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Execution> getStatus(@PathVariable String id) {
        Execution exec = executionService.get(id);
        if (exec == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(exec);
    }

    /**
     * List all executions, most recent first.
     */
    @GetMapping
    public List<Execution> listAll() {
        return executionService.listAll();
    }
}
