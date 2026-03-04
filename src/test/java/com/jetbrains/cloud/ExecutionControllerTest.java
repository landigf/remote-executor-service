package com.jetbrains.cloud;

import com.jetbrains.cloud.model.ExecutionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the execution API.
 *
 * These require Docker to be running on the host machine.
 * If Docker is unavailable, the tests will fail with a clear error
 * from the DockerExecutor layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void submitAndPollUntilFinished() throws Exception {
        // Submit a simple echo command
        String response = mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "script": "echo hello world",
                                "cpuCount": 1,
                                "memoryMb": 256,
                                "image": "alpine:3.19"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the execution ID from the response
        String id = com.fasterxml.jackson.databind.ObjectMapper
                .class.getDeclaredConstructor().newInstance()
                .readTree(response).get("id").asText();

        // Poll until FINISHED (timeout after 30 seconds)
        String status = "QUEUED";
        for (int i = 0; i < 60 && !"FINISHED".equals(status); i++) {
            Thread.sleep(500);
            String pollResponse = mockMvc.perform(get("/api/executions/" + id))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            status = com.fasterxml.jackson.databind.ObjectMapper
                    .class.getDeclaredConstructor().newInstance()
                    .readTree(pollResponse).get("status").asText();
        }

        // Verify the final result
        mockMvc.perform(get("/api/executions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.output").value("hello world"));
    }

    @Test
    void rejectEmptyScript() throws Exception {
        mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "script": "" }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/executions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listExecutions() throws Exception {
        mockMvc.perform(get("/api/executions"))
                .andExpect(status().isOk());
    }
}
