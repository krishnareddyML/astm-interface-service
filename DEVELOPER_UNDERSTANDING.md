# ASTM Interface Service - Developer Understanding Document

## Overview

The ASTM Interface Service is a production-ready, Spring Boot-based bidirectional interface between laboratory instruments and the core LIS (Laboratory Information System) using the ASTM E1381/E1394 protocol. This document provides a complete understanding of the workflow, threading model, message processing, and collision handling mechanisms.

## Architecture Overview

The service consists of three main modules:

### 1. astm-model
- Contains shared POJO classes for ASTM message representation
- Includes records like `HeaderRecord`, `PatientRecord`, `OrderRecord`, `ResultRecord`, etc.
- Used by both server and simulator modules

### 2. astm-server (Main Service)
- Spring Boot application that handles TCP connections from instruments
- Implements ASTM protocol state machine
- Manages message parsing, database storage, and queue publishing
- Handles outbound order processing with collision detection

### 3. instrument-simulator
- Standalone Java application for testing
- Can run `OrthoVisionInstrumentSimulator.java` to simulate instruments
- Supports various test scenarios and JSONL message loading

## Application Startup Flow

### 1. Spring Boot Initialization
```
AstmInterfaceApplication.main()
├── SpringApplication.run()
├── Spring context initialization
├── Configuration loading (application.yml)
└── Component scanning and auto-configuration
```

### 2. ASTM Server Startup (@PostConstruct)
```
ASTMServer.startServer()
├── Create thread pools:
│   ├── serverExecutor (listener threads per instrument)
│   ├── connectionExecutor (connection handler threads)
│   └── keepAliveScheduler (keep-alive management)
├── For each enabled instrument in config:
│   ├── Create ServerSocket on configured port
│   ├── Submit listener task to serverExecutor
│   └── Start accepting connections
└── Log startup completion
```

### 3. Instrument Listener Thread Creation
For each instrument (e.g., OrthoVision on port 9001):
```
ASTMServer.startInstrumentListener()
├── Create ServerSocket bound to instrument port
├── Set socket options (reuse address, timeout)
├── Submit runInstrumentListener() to executor
└── Start accept loop for incoming connections
```

## Connection Flow and Threading Model

### 1. Instrument Connection Accept Loop
```
ASTMServer.runInstrumentListener()
├── while (running && !serverSocket.isClosed())
├── serverSocket.accept() // Blocks with 1-second timeout
├── Configure client socket (TCP_NODELAY, KeepAlive, Read timeout)
├── Create InstrumentDriver instance via reflection
├── Create InstrumentConnectionHandler with:
│   ├── Socket, Driver, InstrumentName
│   ├── ResultQueuePublisher
│   ├── ServerMessageService
│   └── KeepAlive configuration
├── Add handler to connections list
└── Submit handler to connectionExecutor
```

### 2. Per-Connection Handler Thread
```
InstrumentConnectionHandler.run()
├── connected = true
├── Start AstmKeepAliveService (if configured)
├── Main connection loop:
│   ├── handleIncomingMessages() → returns boolean
│   ├── If false: break loop (graceful termination)
│   ├── If exception: log and continue
│   └── Thread.sleep(100) // Prevent busy waiting
└── cleanup() // Close resources
```

### 3. Message Processing Workflow
```
InstrumentConnectionHandler.handleIncomingMessages()
├── protocolStateMachine.receiveMessage()
│   ├── Listen for ENQ from instrument
│   ├── If ENQ received: handleIncomingMessageSynchronized()
│   ├── Send ACK for ENQ
│   ├── Receive frames until EOT
│   ├── Validate checksums and sequence numbers
│   └── Reassemble complete message
├── Save raw message to database (audit trail)
├── Check if keep-alive message
├── Parse message using instrument driver
├── Publish results to RabbitMQ queue
└── Handle orders (bidirectional communication)
```

## Protocol State Machine Details

### States
- **IDLE**: Waiting for incoming ENQ or ready to send
- **WAITING_FOR_ACK**: Sent ENQ, waiting for ACK response
- **RECEIVING**: Actively receiving frames from instrument
- **TRANSMITTING**: Sending frames to instrument
- **ERROR**: Protocol error state

### Thread Safety Model
The `ASTMProtocolStateMachine` uses a sophisticated threading model:

```java
// NON-SYNCHRONIZED for idle listening (allows keep-alive)
public String receiveMessage() throws IOException {
    while (true) {
        int receivedChar = inputStream.read(); // Not synchronized
        if (receivedChar == ENQ) {
            return handleIncomingMessageSynchronized(); // Synchronized
        }
    }
}

// SYNCHRONIZED for actual message processing
private synchronized String handleIncomingMessageSynchronized() {
    // Send ACK, receive frames, validate, reassemble
}
```

This design allows the keep-alive service to send messages during idle periods without blocking incoming message reception.

## Bidirectional Communication and Collision Handling

### Incoming Message Flow (Instrument → LIS)
```
Instrument connects → Sends ENQ → Server sends ACK → Instrument sends frames
├── Server validates checksums and sequence numbers
├── Server acknowledges each frame with ACK
├── Server reassembles complete message
├── Save to database for audit trail
├── Parse using instrument driver (OrthoVisionDriver)
├── Publish results to instrument-specific queue
└── Mark as processed and published in database
```

### Outbound Order Flow (LIS → Instrument)
```
OrderQueueListener.handleOrderMessage()
├── Parse JSON order message
├── Save to ASTMOrderMessages table (database-backed processing)
├── Attempt immediate processing:
│   ├── Get connection handler for target instrument
│   ├── Check if instrument connected
│   ├── Check if instrument busy (collision detection)
│   ├── If available: send message immediately
│   └── If busy/disconnected: schedule retry
└── Scheduled retry processor handles failed attempts
```

### Collision Detection Mechanism
```java
// In OrderMessageService.processOrderMessage()
InstrumentConnectionHandler handler = astmServer.getConnectionHandler(instrumentName);

if (handler == null) {
    // No connection - schedule retry for later
    scheduleRetry(orderMessage, connectionRetryDelayMinutes, "No connection handler");
    return false;
}

if (!handler.isConnected()) {
    // Instrument disconnected - schedule retry
    scheduleRetry(orderMessage, connectionRetryDelayMinutes, "Instrument disconnected");
    return false;
}

if (handler.isBusy()) {
    // Instrument busy (not in IDLE state) - schedule collision retry
    scheduleRetry(orderMessage, collisionRetryDelayMinutes, 
                "Instrument busy: " + handler.getProtocolStateMachine().getCurrentState());
    return false;
}

// Clear to send
boolean success = handler.sendMessage(astmMessage);
```

### Database-Backed Message Processing
Orders are persisted in the `ASTMOrderMessages` table with retry tracking:

```sql
CREATE TABLE ASTMOrderMessages (
    Id BIGINT IDENTITY(1,1) PRIMARY KEY,
    MessageId NVARCHAR(255) NOT NULL,
    InstrumentName NVARCHAR(100) NOT NULL,
    MessageContent NVARCHAR(MAX) NOT NULL,
    Status NVARCHAR(20) NOT NULL, -- PENDING, PROCESSING, SUCCESS, FAILED
    RetryCount INT NOT NULL DEFAULT 0,
    MaxRetryAttempts INT NOT NULL DEFAULT 5,
    CreatedAt DATETIME2 NOT NULL,
    UpdatedAt DATETIME2 NOT NULL,
    LastRetryAt DATETIME2,
    NextRetryAt DATETIME2,
    ErrorMessage NVARCHAR(MAX)
);
```

### Scheduled Retry Processing
```java
@Scheduled(fixedDelayString = "${lis.messaging.retry.schedule-interval-ms:600000}")
public void processRetries() {
    List<OrderMessage> readyMessages = repository.findMessagesReadyForRetry(batchSize);
    
    for (OrderMessage message : readyMessages) {
        // Mark as PROCESSING to prevent concurrent processing
        if (repository.markAsProcessing(message.getId())) {
            processOrderMessage(message);
        }
    }
}
```

## Keep-Alive Mechanism

### Server-Side Keep-Alive Service
Each connection handler creates an `AstmKeepAliveService`:

```java
AstmKeepAliveService keepAliveService = new AstmKeepAliveService(
    instrumentName, 
    keepAliveIntervalMinutes, 
    protocolStateMachine, 
    scheduler
);
keepAliveService.start(); // Schedules periodic keep-alive messages
```

The keep-alive service:
1. Schedules periodic execution every N minutes
2. Builds standard ASTM keep-alive message (H + L records)
3. Sends via protocol state machine during idle periods
4. Maintains connection during periods of no instrument activity

### Instrument Simulator Keep-Alive
The `OrthoVisionInstrumentSimulator` can also send keep-alive messages:
```java
// Menu option 6: Start periodic keep-alive
startKeepAlive(60); // Every 60 seconds

// Builds keep-alive message:
String keepAliveMessage = 
    "H|\\^&|||OCD^VISION^5.14.0.47342^JNumber|||||||P|LIS2-A|" + timestamp + "\r" +
    "L||\r";
```

## Message Processing Pipeline

### 1. Raw Message Storage (Safety First)
```java
// STEP 1: Save raw message to database FIRST for safety/audit
ServerMessage savedMessage = serverMessageService.saveIncomingMessage(
    rawMessage, instrumentName, remoteAddress, messageType);
```

### 2. Keep-Alive Detection
```java
// Check if this is a keep-alive message first
if (keepAliveService != null && keepAliveService.handleIncomingKeepAlive(rawMessage)) {
    serverMessageService.markAsProcessed(savedMessage.getId(), null);
    return true; // Keep-alive processed, continue main loop
}
```

### 3. Message Parsing
```java
// STEP 2: Parse the message using the instrument driver
AstmMessage parsedMessage = driver.parse(rawMessage);
parsedMessage.setInstrumentName(instrumentName);
serverMessageService.markAsProcessed(savedMessage.getId(), parsedMessage);
```

### 4. Result Publishing
```java
// STEP 3: Publish results to message queue
if (parsedMessage.hasResults()) {
    resultPublisher.publishResult(parsedMessage);
    serverMessageService.markAsPublished(savedMessage.getId());
}
```

### 5. Order Handling
```java
// Handle orders if present (for bidirectional communication)
if (parsedMessage.hasOrders()) {
    // Orders typically handled by separate processing logic
    serverMessageService.markAsPublished(savedMessage.getId());
}
```

## Instrument Driver System

### Driver Interface
```java
public interface InstrumentDriver {
    AstmMessage parse(String rawMessage) throws Exception;
    String build(AstmMessage message) throws Exception;
    String getInstrumentName();
    String getAstmVersion();
    boolean supportsMessage(String rawMessage);
    String getDriverConfiguration();
}
```

### OrthoVision Driver Implementation
The `OrthoVisionDriver` handles Ortho Vision-specific ASTM format:
- Field delimiter: `|`
- Component delimiter: `^`
- Date format: `yyyyMMddHHmmss`
- Supports all standard ASTM record types (H, P, O, R, M, L)

### Driver Creation
Drivers are created dynamically via reflection:
```java
private InstrumentDriver createInstrumentDriver(AppConfig.InstrumentConfig config) {
    Class<?> driverClass = Class.forName(config.getDriverClassName());
    return (InstrumentDriver) driverClass.getDeclaredConstructor().newInstance();
}
```

## Configuration System

### Instrument Configuration
```yaml
lis:
  instruments:
    - name: OrthoVision
      port: 9001
      driverClassName: com.lis.astm.server.driver.impl.OrthoVisionDriver
      maxConnections: 5
      enabled: true
      connectionTimeoutSeconds: 30
      keepAliveIntervalMinutes: 5
      orderQueueName: ortho-vision.orders.queue
      resultQueueName: ortho-vision.results.queue
      exchangeName: ortho-vision-exchange
```

### Messaging Configuration
```yaml
lis:
  messaging:
    enabled: true
    retry:
      batch-size: 10
      max-attempts: 5
      collision-delay-minutes: 30
      connection-delay-minutes: 60
      schedule-interval-ms: 600000  # 10 minutes
```

## Error Handling and Resilience

### Connection Resilience
- Socket timeouts prevent hanging connections (6 minutes)
- Keep-alive maintains healthy connections during idle periods
- Graceful connection termination when instruments disconnect
- Automatic resource cleanup on connection close

### Message Processing Resilience
- Database-first approach for incoming messages (audit trail)
- Retry mechanism for failed queue publishing
- Collision detection and retry for outbound orders
- Exponential backoff for connection failures

### Thread Safety
- Protocol state machine uses selective synchronization
- Connection handlers run in isolated threads
- Thread-safe collections for active connection tracking
- Proper resource cleanup on thread termination

## Instrument Simulator Workflow

### OrthoVisionInstrumentSimulator Usage
```bash
# Compile and run
javac OrthoVisionInstrumentSimulator.java
java OrthoVisionInstrumentSimulator localhost 9001
```

### Menu Options
1. **Send KEEP-ALIVE**: Sends H + L records to maintain connection
2. **Send HOST QUERY**: Queries for orders by Sample ID, then waits for incoming messages
3. **Send BASIC RESULT**: Sends ABO/Rh results with M-records
4. **Send CROSSMATCH RESULT**: Sends crossmatch compatibility results
5. **Send CANCELLED ORDER**: Sends cancelled order status
6. **Start periodic KEEP-ALIVE**: Automated keep-alive every N seconds
7. **Receive ONE transmission**: Listen for incoming orders from LIS
8. **Load JSONL**: Load test messages from JSON Lines file

### JSONL Test Data Format
```json
{"testcase": 1, "data": "H|\\^&|||OCD^VISION^5.13.1.46935|||||||P|LIS2-A|20230915123456\rP|1|PID123||NID123|Patient^Test||19900101|M\rL||"}
{"testcase": 2, "data": "H|\\^&|||OCD^VISION^5.13.1.46935|||||||P|LIS2-A|20230915123456\rR|1|ABO|A|||||F||Automatic||20230915123456|JNumber\rL||"}
```

## Concurrent Scenario Handling

### Scenario 1: Outbound Order During Incoming Result
```
Time 1: Instrument sends ENQ (wants to send result)
Time 2: LIS receives order for same instrument
Time 3: Protocol state machine is in RECEIVING state
Time 4: Order processor detects isBusy() = true
Time 5: Order scheduled for retry in 30 minutes
Time 6: Instrument completes result transmission
Time 7: Next retry cycle processes queued order
```

### Scenario 2: Multiple Orders for Same Instrument
```
Order A arrives → Saved to database → Immediate processing starts
Order B arrives → Saved to database → Detects collision → Scheduled for retry
Order A completes → Instrument becomes IDLE
Next retry cycle → Order B processed successfully
```

### Scenario 3: Connection Loss During Processing
```
Order processing starts → Connection check passes → Send attempt fails
Order marked for retry → Connection handler detects disconnect → Cleanup
Instrument reconnects → New connection handler created
Next retry cycle → Order processed on new connection
```

## Performance Characteristics

### Thread Pool Configuration
- **Server Executor**: 2-N threads (one per instrument listener)
- **Connection Executor**: 8-64 threads (for connection handlers)
- **Keep-Alive Scheduler**: 4 threads (for periodic tasks)

### Timeout Settings
- **Socket Read Timeout**: 6 minutes (prevents stale connections)
- **ACK Wait Timeout**: 15 seconds (protocol-level acknowledgment)
- **Connection Timeout**: 30 seconds (initial connection establishment)

### Retry Configuration
- **Collision Retry Delay**: 30 minutes (instrument busy)
- **Connection Retry Delay**: 60 minutes (instrument offline)
- **Max Retry Attempts**: 5 attempts per message
- **Batch Size**: 10 messages per retry cycle

## Monitoring and Observability

### Database Audit Trail
- All incoming messages stored in `ASTMServerMessages` table
- All outbound orders tracked in `ASTMOrderMessages` table
- Processing status and timestamps for full traceability

### Logging
- Connection events (connect, disconnect, errors)
- Message processing (receive, parse, publish)
- Protocol state transitions
- Retry processing and collision detection

### Health Endpoints
- Server status via `ASTMServer.getServerStatus()`
- Connection details via `getConnectionDetails()`
- Message statistics via repositories

This architecture provides a robust, scalable, and maintainable solution for ASTM protocol communication with comprehensive error handling, collision detection, and monitoring capabilities.
