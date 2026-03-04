# Remote Command Execution Service

A REST service that executes shell commands on isolated remote executors. Built with Java 17 and Spring Boot 3, using Docker containers as the execution backend.

## Architecture

```
                        ┌──────────────────────────────────────┐
                        │          Spring Boot Service          │
                        │                                      │
  POST /api/executions  │  ┌────────────┐   ┌───────────────┐  │    ┌─────────────────┐
  ─────────────────────►│  │ Controller  ├──►│ExecutionService├──┼───►│ Docker Container │
                        │  └────────────┘   └───────┬───────┘  │    │  (remote executor)│
  GET /api/executions/  │                           │          │    │                   │
       {id}             │              ┌────────────┘          │    │  /bin/sh -c "..." │
  ─────────────────────►│              │ ConcurrentHashMap     │    └─────────────────┘
                        │              │ (execution state)     │
                        └──────────────┴───────────────────────┘
```

When a user submits a script, the service:

1. **Queues** the request and returns immediately (status: `QUEUED`)
2. **Starts** a Docker container with the requested CPU/memory limits
3. **Waits** for the container to initialise
4. **Executes** the script inside the container (status: `IN_PROGRESS`)
5. **Collects** stdout, exit code, and timestamps (status: `FINISHED`)
6. **Tears down** the container

### Why Docker?

Docker containers mirror the lifecycle of cloud VMs (EC2 instances, K8s pods): start, constrain resources, run work, destroy. The `DockerExecutor` class encapsulates all Docker CLI calls behind a clean interface, so replacing it with an AWS SDK or Kubernetes client is a localised change.

## Prerequisites

- Java 17+
- Docker daemon running
- Gradle 8+ (or use the wrapper)

## Running

```bash
./gradlew bootRun
```

The server starts on `http://localhost:8080`.

## API

### Submit an execution

```bash
curl -X POST http://localhost:8080/api/executions \
  -H "Content-Type: application/json" \
  -d '{
    "script": "echo hello world && uname -a",
    "cpuCount": 1,
    "memoryMb": 256,
    "image": "alpine:3.19"
  }'
```

Response:
```json
{
  "id": "a1b2c3d4e5f6",
  "script": "echo hello world && uname -a",
  "cpuCount": 1,
  "memoryMb": 256,
  "image": "alpine:3.19",
  "status": "QUEUED",
  "createdAt": 1709573000000
}
```

### Poll execution status

```bash
curl http://localhost:8080/api/executions/a1b2c3d4e5f6
```

Response (after completion):
```json
{
  "id": "a1b2c3d4e5f6",
  "status": "FINISHED",
  "output": "hello world\nLinux 84a3f2... 6.5.0 ...",
  "exitCode": 0,
  "startedAt": 1709573002000,
  "finishedAt": 1709573003000
}
```

### List all executions

```bash
curl http://localhost:8080/api/executions
```

## Testing

```bash
./gradlew test
```

Integration tests require Docker to be running.

## Design decisions

- **In-memory state** (`ConcurrentHashMap`): keeps things simple for a task submission. In production this would be a database or Redis.
- **Thread pool per execution**: each submission gets its own thread. For high throughput, a bounded pool or coroutine-based approach would be better.
- **Resource constraints forwarded to Docker**: `--cpus` and `-m` flags map directly to the user's `cpuCount` and `memoryMb` request fields.
- **Alpine as default would be faster**: the default image is `ubuntu:22.04` for broad compatibility, but `alpine:3.19` pulls faster and is a better choice for lightweight scripts.
