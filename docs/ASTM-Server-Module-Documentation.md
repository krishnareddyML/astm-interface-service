# ASTM Server Module - Comprehensive Documentation

## Table of Contents
1. [Overview](#overview)
2. [Core Package (com.lis.astm.server.core)](#core-package)
3. [Configuration Package (com.lis.astm.server.config)](#configuration-package)
4. [Driver Package (com.lis.astm.server.driver)](#driver-package)
5. [Messaging Package (com.lis.astm.server.messaging)](#messaging-package)
6. [Protocol Package (com.lis.astm.server.protocol)](#protocol-package)
7. [Service Package (com.lis.astm.server.service)](#service-package)
8. [Application Entry Point](#application-entry-point)

---

## Overview

The ASTM Server Module is a Spring Boot-based service that implements the ASTM E1394-97 protocol for communication with laboratory instruments, specifically supporting ORTHO VISION® systems. It provides TCP-based bidirectional communication, message parsing, result publishing to RabbitMQ, and keep-alive functionality to maintain persistent connections.

### Architecture Principles
- **Fault Isolation**: Each instrument connection runs in isolated threads
- **Scalability**: Supports multiple instruments on different ports
- **Protocol Compliance**: Full ASTM E1394-97 specification implementation
- **Reliability**: Keep-alive mechanism prevents connection drops
- **Flexibility**: Pluggable driver architecture for different instruments

---

## Core Package (com.lis.astm.server.core)

### ASTMServer.java

**Overall Objective**: Main TCP server component that manages multiple instrument listeners and connection handlers across different ports.

**Key Responsibilities**:
- Initialize and manage TCP listeners for configured instruments
- Handle incoming connections with connection limits
- Provide thread pool management for server and connection operations
- Maintain active connection registry with monitoring capabilities
- Integrate keep-alive scheduling for all connections

**Method-Level Objectives**:

1. **`startServer()`**
   - Initialize thread pools (server, connection, keep-alive)
   - Start TCP listeners for each enabled instrument
   - Setup monitoring and management infrastructure

2. **`stopServer()`**
   - Gracefully shutdown all server tasks and connections
   - Close server sockets and cleanup resources
   - Terminate thread pools with proper cleanup

3. **`startInstrumentListener(AppConfig.InstrumentConfig config)`**
   - Create ServerSocket for specific instrument port
   - Initialize connection tracking for the instrument
   - Submit listener task to server executor

4. **`runInstrumentListener(AppConfig.InstrumentConfig config, ServerSocket serverSocket)`**
   - Main listener loop accepting incoming connections
   - Enforce connection limits per instrument
   - Create and initialize connection handlers
   - Handle connection lifecycle management

5. **`createInstrumentDriver(AppConfig.InstrumentConfig config)`**
   - Use reflection to instantiate instrument-specific drivers
   - Provide error handling for driver creation failures
   - Support dynamic driver loading based on configuration

6. **`getServerStatus()`**
   - Provide runtime status information
   - Include active listener and connection counts
   - Support monitoring and health check operations

7. **`getConnectionDetails()`**
   - Retrieve detailed connection information
   - Support operational monitoring and debugging
   - Provide connection statistics and state information

8. **`getConnectionHandler(String instrumentName)`**
   - Locate active connection handler for specific instrument
   - Support message routing and bidirectional communication
   - Return first active connection for the instrument

### InstrumentConnectionHandler.java

**Overall Objective**: Manages individual instrument connections in isolated threads, providing fault tolerance and protocol-specific message handling.

**Key Responsibilities**:
- Handle TCP connection lifecycle for single instrument
- Process incoming ASTM messages with protocol state management
- Integrate keep-alive service for connection maintenance
- Support bidirectional message transmission
- Provide connection monitoring and statistics

**Method-Level Objectives**:

1. **`InstrumentConnectionHandler(Socket, InstrumentDriver, String, ResultQueuePublisher, int, ScheduledExecutorService)`**
   - Initialize connection with socket and driver
   - Setup protocol state machine for ASTM communication
   - Configure keep-alive service based on interval settings
   - Prepare connection for message processing

2. **`run()`**
   - Main connection processing loop
   - Start keep-alive service if configured
   - Handle incoming messages with error recovery
   - Manage connection state and lifecycle

3. **`handleIncomingMessages()`**
   - Receive ASTM messages using protocol state machine
   - Filter and process keep-alive messages
   - Parse messages using instrument driver
   - Publish results to message queue

4. **`sendMessage(AstmMessage message)`**
   - Build ASTM message string using driver
   - Transmit message using protocol state machine
   - Provide error handling and status reporting
   - Support external message sending requests

5. **`isConnected()`**
   - Check connection status and socket state
   - Support health monitoring and connection validation
   - Used by external components for connection checks

6. **`getConnectionStats()`**
   - Provide detailed connection information
   - Include protocol state and connection details
   - Support monitoring and debugging operations

7. **`stop()`**
   - Gracefully stop connection handler
   - Interrupt processing thread if needed
   - Initiate cleanup procedures

8. **`cleanup()`**
   - Stop keep-alive service
   - Close protocol state machine and socket
   - Release all resources and update connection state

---

## Configuration Package (com.lis.astm.server.config)

### AppConfig.java

**Overall Objective**: Central configuration management for ASTM Interface Service using Spring Boot's configuration properties binding.

**Key Responsibilities**:
- Load instrument configurations from application.yml
- Provide messaging queue configuration
- Support keep-alive interval configuration
- Enable instrument-specific and global settings

**Method-Level Objectives**:

#### Main Class Methods:
1. **`getInstruments()` / `setInstruments()`**
   - Access list of instrument configurations
   - Support dynamic configuration loading

2. **`getMessaging()` / `setMessaging()`**
   - Access global messaging configuration
   - Provide fallback settings for instruments

#### InstrumentConfig Inner Class:
1. **`getKeepAliveIntervalMinutes()` / `setKeepAliveIntervalMinutes()`**
   - Configure keep-alive intervals (0-1440 minutes)
   - Control connection maintenance behavior

2. **`isKeepAliveEnabled()`**
   - Validate keep-alive configuration
   - Determine if keep-alive should be activated

3. **`getEffectiveOrderQueueName(MessagingConfig globalMessaging)`**
   - Resolve instrument-specific vs global queue names
   - Support flexible queue routing strategies

4. **`getEffectiveResultQueueName(MessagingConfig globalMessaging)`**
   - Resolve result queue names with fallback logic
   - Enable instrument isolation or shared queues

#### MessagingConfig Inner Class:
1. **`getDefaultOrderQueueName(String instrumentName)`**
   - Generate standard queue names for instruments
   - Support consistent naming conventions

2. **Queue Configuration Methods**
   - Manage exchange names and queue prefixes
   - Enable/disable messaging functionality

---

## Driver Package (com.lis.astm.server.driver)

### InstrumentDriver.java (Interface)

**Overall Objective**: Define contract for all instrument-specific drivers to ensure consistent ASTM message processing capabilities.

**Key Responsibilities**:
- Standardize message parsing and building interfaces
- Support instrument-specific protocol variations
- Enable pluggable driver architecture
- Provide message validation capabilities

**Method-Level Objectives**:

1. **`parse(String rawMessage)`**
   - Convert raw ASTM message strings to structured objects
   - Handle instrument-specific message formats
   - Provide error handling for malformed messages

2. **`build(AstmMessage message)`**
   - Generate ASTM-compliant message strings
   - Apply instrument-specific formatting rules
   - Support bidirectional communication

3. **`getInstrumentName()`**
   - Identify supported instrument type
   - Enable driver selection and routing

4. **`getAstmVersion()`**
   - Specify ASTM protocol version compliance
   - Support version-specific behavior

5. **`supportsMessage(String rawMessage)`**
   - Validate message compatibility with driver
   - Enable message routing to appropriate drivers

### OrthoVisionDriver.java (Implementation)

**Overall Objective**: Concrete implementation of InstrumentDriver specifically for Ortho Vision instruments with full ASTM E1394-97 compliance.

**Key Responsibilities**:
- Parse Ortho Vision specific ASTM message formats
- Handle crossmatch test result processing
- Support bidirectional order and result communication
- Implement ORTHO VISION® field specifications

**Method-Level Objectives**:

1. **`parse(String rawMessage)`**
   - Split message into ASTM records by line breaks
   - Parse each record type (Header, Patient, Order, Result)
   - Determine message type based on content
   - Handle Ortho Vision specific field formats

2. **`build(AstmMessage message)`**
   - Build Header record with sender identification
   - Generate Patient record if present
   - Create Order records with proper sequencing
   - Format Result records with crossmatch support

3. **Record Building Methods**:
   - **`buildHeaderRecord()`**: Format H record with instrument identification
   - **`buildPatientRecord()`**: Format P record with patient demographics
   - **`buildOrderRecord()`**: Format O record with test orders and Universal Test IDs
   - **`buildResultRecord()`**: Format R record with test results and status

4. **Record Parsing Methods**:
   - **`parseRecord()`**: Route records to appropriate parser based on type
   - **`parseHeaderRecord()`**: Extract sender info and message control
   - **`parsePatientRecord()`**: Extract patient demographics and identifiers
   - **`parseOrderRecord()`**: Extract test orders and crossmatch specifications
   - **`parseResultRecord()`**: Extract test results with proper data types

5. **Utility Methods**:
   - **`determineMessageType()`**: Classify message based on content
   - **`parseDateTime()`**: Handle ASTM datetime format conversion
   - **`isEmpty()`**: Validate field content for processing

---

## Messaging Package (com.lis.astm.server.messaging)

### ResultQueuePublisher.java

**Overall Objective**: Publish parsed ASTM result messages to RabbitMQ queues for consumption by the Core LIS system.

**Key Responsibilities**:
- Convert ASTM messages to JSON format
- Route messages to instrument-specific queues
- Add metadata headers for message processing
- Handle messaging failures and error conditions

**Method-Level Objectives**:

1. **`ResultQueuePublisher()` (Constructor)**
   - Initialize ObjectMapper with JavaTimeModule
   - Setup JSON serialization for ASTM messages
   - Configure datetime serialization support

2. **`publishResult(AstmMessage astmMessage)`**
   - Validate message and messaging configuration
   - Find instrument configuration for queue routing
   - Convert ASTM message to JSON format
   - Publish to appropriate queue with headers

3. **Queue Resolution Methods**:
   - **`findInstrumentConfig()`**: Locate configuration for instrument
   - **Queue Name Resolution**: Determine effective queue names
   - **Exchange Routing**: Support direct queue or exchange-based routing

4. **Message Enhancement**:
   - **Header Addition**: Add instrumentName, messageType, counts, timestamp
   - **Metadata Enrichment**: Include result and order counts
   - **Error Handling**: Log failures and support retry mechanisms

5. **Publishing Strategies**:
   - **Exchange-based**: Use routing keys for message distribution
   - **Direct Queue**: Publish directly to named queues
   - **Header Customization**: Add processing metadata

---

## Protocol Package (com.lis.astm.server.protocol)

### ASTMProtocolStateMachine.java

**Overall Objective**: Implement the low-level ASTM E1381 protocol state machine for reliable message transmission with proper handshaking.

**Key Responsibilities**:
- Manage ENQ/ACK/NAK/EOT handshake protocol
- Handle frame transmission with checksum validation
- Provide timeout management for protocol operations
- Support reliable message delivery with retries

**Method-Level Objectives**:

1. **`ASTMProtocolStateMachine(Socket, String)` (Constructor)**
   - Initialize socket streams for communication
   - Set socket timeout for read operations
   - Initialize state machine to IDLE state
   - Setup frame numbering and tracking

2. **Protocol Control Methods**:
   - **`sendEnq()`**: Initiate transmission with Enquiry
   - **`sendAck()`**: Acknowledge successful frame receipt
   - **`sendNak()`**: Signal frame transmission error
   - **`sendEot()`**: End transmission sequence

3. **Message Transmission**:
   - **`sendMessage(String)`**: Complete message send with protocol
   - **`sendFrame(String)`**: Send individual frame with checksum
   - **`waitForAck()`**: Wait for acknowledgment with timeout
   - **Retry Logic**: Handle transmission failures

4. **Message Reception**:
   - **`receiveMessage()`**: Complete message receive with protocol
   - **`receiveFrame()`**: Receive individual frame with validation
   - **`assembleMessage()`**: Combine frames into complete message
   - **Checksum Validation**: Verify frame integrity

5. **State Management**:
   - **`getCurrentState()`**: Provide current protocol state
   - **State Transitions**: Manage protocol state changes
   - **Error Recovery**: Handle protocol errors and timeouts

6. **Utility Methods**:
   - **`close()`**: Cleanup protocol resources
   - **Frame Management**: Handle frame numbering and assembly
   - **Timeout Handling**: Manage protocol timeouts

---

## Service Package (com.lis.astm.server.service)

### AstmKeepAliveService.java

**Overall Objective**: Implement ASTM keep-alive protocol to maintain TCP connections during idle periods, preventing ORTHO VISION® "Apsw26 Unable to Connect to the LIS" errors.

**Key Responsibilities**:
- Send periodic keep-alive messages at configured intervals
- Handle incoming keep-alive message detection and response
- Provide statistics and monitoring for keep-alive operations
- Integrate with protocol state machine for message transmission

**Method-Level Objectives**:

1. **`AstmKeepAliveService()` (Constructor)**
   - Initialize with instrument name and interval configuration
   - Setup protocol state machine and scheduler references
   - Validate interval configuration (1-1440 minutes)
   - Prepare for keep-alive operation scheduling

2. **Service Lifecycle Methods**:
   - **`start()`**: Begin scheduled keep-alive transmission
   - **`stop()`**: Stop keep-alive service and cancel tasks
   - **Synchronization**: Thread-safe service state management

3. **Keep-Alive Protocol Implementation**:
   - **`sendKeepAlive()`**: Execute complete keep-alive message sequence
   - **`buildKeepAliveMessage()`**: Generate ASTM keep-alive message format
   - **Protocol Flow**: Implement ENQ/ACK/Frame/ACK/EOT sequence

4. **Message Detection and Handling**:
   - **`handleIncomingKeepAlive(String)`**: Detect and process keep-alive messages
   - **`isKeepAliveMessage(String)`**: Identify keep-alive message patterns
   - **Response Handling**: Provide appropriate protocol responses

5. **Statistics and Monitoring**:
   - **`getKeepAliveStats()`**: Provide operational statistics
   - **`isEnabled()`**: Check service status
   - **`getLastKeepAliveSent()`**: Track last transmission time
   - **`getLastKeepAliveReceived()`**: Track last reception time

6. **Error Handling**:
   - **Connection Error Recovery**: Handle transmission failures
   - **Protocol Error Handling**: Manage keep-alive protocol errors
   - **Logging and Monitoring**: Provide detailed operational logging

---

## Application Entry Point

### AstmInterfaceApplication.java

**Overall Objective**: Spring Boot main application class that bootstraps the ASTM Interface Service with proper configuration and component scanning.

**Key Responsibilities**:
- Initialize Spring Boot application context
- Configure component scanning for service discovery
- Enable Spring Boot auto-configuration features
- Provide application entry point for deployment

**Method-Level Objectives**:

1. **`main(String[] args)`**
   - Launch Spring Boot application
   - Initialize application context
   - Start all configured services and listeners

2. **Class-Level Configuration**:
   - **`@SpringBootApplication`**: Enable auto-configuration and component scanning
   - **`@ComponentScan`**: Discover and register service components
   - **Package Scanning**: Include all necessary service packages

**Application Architecture**:
- **Spring Boot 2.7.18**: Maintained for Java 1.8 compatibility
- **Component-based**: Automatic discovery and wiring of services
- **Configuration-driven**: External configuration via application.yml
- **Production-ready**: Health checks, monitoring, and management endpoints

---

## Integration Points

### Message Flow Architecture
1. **Instrument Connection**: TCP connection established to ASTMServer
2. **Message Reception**: InstrumentConnectionHandler receives ASTM messages
3. **Protocol Processing**: ASTMProtocolStateMachine handles low-level protocol
4. **Message Parsing**: OrthoVisionDriver converts raw messages to structured objects
5. **Result Publishing**: ResultQueuePublisher sends results to RabbitMQ
6. **Keep-Alive Maintenance**: AstmKeepAliveService maintains connection health

### Configuration Dependencies
- **AppConfig**: Central configuration loaded from application.yml
- **Instrument Configuration**: Per-instrument settings including ports and drivers
- **Messaging Configuration**: Queue names and RabbitMQ settings
- **Keep-Alive Configuration**: Interval settings for connection maintenance

### Error Handling Strategy
- **Fault Isolation**: Errors in one connection don't affect others
- **Graceful Degradation**: Service continues despite individual component failures
- **Comprehensive Logging**: Detailed error logging for troubleshooting
- **Resource Cleanup**: Proper cleanup on errors and shutdown

This documentation provides a complete understanding of the ASTM Server Module architecture, enabling effective maintenance, troubleshooting, and enhancement of the ASTM interface functionality.
