# ASTM Interface Service

A comprehensive Java Spring Boot application that acts as a bidirectional ASTM interface service between laboratory instruments and the core Laboratory Information System (LIS).

## Overview

The ASTM Interface Service implements the ASTM E1381/E1394 protocol to enable communication between clinical laboratory instruments and the core LIS. The service is designed to run as a standalone Windows Service, handling multiple instrument connections simultaneously while integrating with a message queue system for communication with the Core LIS.

## Architecture

### Core Principles

- **Decoupling**: Complete separation from Core LIS database through message queue communication
- **Multi-Instrument Support**: Simultaneous connections from different instrument vendors
- **Driver-Based Design**: Extensible driver pattern for instrument-specific protocol variations
- **Fault Isolation**: Each instrument connection runs in isolated threads
- **Stateful Protocol Handling**: Full ASTM E1381 low-level protocol implementation

### Technology Stack

- **Language**: Java 1.8
- **Framework**: Spring Boot 2.7.x
- **Build Tool**: Maven
- **Message Queue**: RabbitMQ (via Spring AMQP)
- **Protocol**: ASTM E1381/E1394

## Project Structure

```
astm-interface-service/
├── pom.xml                   # Parent POM
├── astm-model/              # Shared data models
├── astm-server/             # Main server application
└── instrument-simulator/    # Testing simulator
```

### Module: astm-model

Contains POJO classes representing ASTM message components:
- `HeaderRecord.java` - ASTM Header (H) records
- `PatientRecord.java` - ASTM Patient (P) records  
- `OrderRecord.java` - ASTM Order (O) records
- `ResultRecord.java` - ASTM Result (R) records
- `TerminatorRecord.java` - ASTM Terminator (L) records
- `AstmMessage.java` - Container for complete ASTM messages

### Module: astm-server

Main Spring Boot application with components:

#### Configuration
- `AppConfig.java` - Application configuration from YAML

#### Protocol Layer
- `ChecksumUtils.java` - ASTM checksum calculations
- `ASTMProtocolStateMachine.java` - Low-level protocol handling

#### Driver Layer
- `InstrumentDriver.java` - Interface for instrument drivers
- `OrthoVisionDriver.java` - Example Ortho Vision implementation

#### Core Services
- `ASTMServer.java` - Main TCP server managing multiple ports
- `InstrumentConnectionHandler.java` - Individual connection handler

#### Messaging
- `ResultQueuePublisher.java` - Publishes results to message queue
- `OrderQueueListener.java` - Listens for outbound orders

### Module: instrument-simulator

Command-line testing tool that simulates laboratory instruments:
- `InstrumentSimulator.java` - Interactive ASTM instrument simulator

## Quick Start

### Prerequisites

- Java 1.8 or higher
- Maven 3.6+
- RabbitMQ server (optional, for message queue features)

### Building the Project

```bash
# Clone and build all modules
cd astm-interface-service
mvn clean install
```

### Running the Server

```bash
# Run the main server
cd astm-server
mvn spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar astm-server/target/astm-server-1.0.0.jar
```

### Running the Simulator

```bash
# Build the simulator
cd instrument-simulator
mvn clean package

# Run the simulator
java -jar target/instrument-simulator-1.0.0-shaded.jar

# Or with custom settings
java -jar target/instrument-simulator-1.0.0-shaded.jar --host localhost --port 9001 --name "TestInstrument"
```

## Configuration

### Server Configuration (application.yml)

```yaml
lis:
  instruments:
    - name: OrthoVision
      port: 9001
      driverClassName: com.lis.astm.server.driver.impl.OrthoVisionDriver
      enabled: true
      maxConnections: 5
      connectionTimeoutSeconds: 30
  
  messaging:
    enabled: true
    orderQueueName: lis.orders.outbound
    resultQueueName: lis.results.inbound
    exchangeName: lis.exchange

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### Adding New Instruments

1. Create a new driver class implementing `InstrumentDriver`:

```java
@Component
public class MyInstrumentDriver implements InstrumentDriver {
    @Override
    public AstmMessage parse(String rawMessage) throws Exception {
        // Implement instrument-specific parsing
    }
    
    @Override
    public String build(AstmMessage message) throws Exception {
        // Implement instrument-specific message building
    }
    
    // ... other required methods
}
```

2. Add configuration in `application.yml`:

```yaml
lis:
  instruments:
    - name: MyInstrument
      port: 9003
      driverClassName: com.lis.astm.server.driver.impl.MyInstrumentDriver
      enabled: true
```

## ASTM Protocol Implementation

### Message Flow

1. **Incoming Results** (Instrument → LIS):
   - Instrument connects to configured port
   - Sends ENQ to initiate transmission
   - Server responds with ACK
   - Instrument sends framed ASTM message
   - Server validates checksums and sends ACK/NAK
   - Complete message is parsed and published to result queue

2. **Outgoing Orders** (LIS → Instrument):
   - Core LIS publishes order to message queue
   - OrderQueueListener receives message
   - Message is sent to appropriate instrument connection
   - ASTM protocol handshake manages transmission

### Protocol Features

- **Checksum Validation**: Full ASTM checksum calculation and validation
- **Frame Management**: Automatic message segmentation for large payloads
- **Error Handling**: NAK/retransmission support
- **State Management**: Proper ENQ/ACK/EOT sequence handling
- **Connection Pooling**: Multiple simultaneous connections per instrument

## Message Queue Integration

### Result Publishing

Results are automatically published to the configured queue with headers:
- `instrumentName`: Source instrument
- `messageType`: Type of ASTM message (RESULT, ORDER, QUERY)
- `resultCount`: Number of results in message
- `timestamp`: Processing timestamp

### Order Processing

The service listens for outbound orders and routes them to connected instruments based on the `instrumentName` field in the message.

## Testing

### Using the Simulator

The instrument simulator provides several testing modes:

1. **Sample Results**: Generate and send realistic test results
2. **Custom Messages**: Send manually entered ASTM messages  
3. **File-based**: Send ASTM messages from text files
4. **Order Listening**: Receive orders from the server
5. **Connection Testing**: Verify server connectivity

### Sample ASTM Messages

Sample messages are provided in the simulator resources:
- `sample-cbc-results.astm` - Complete Blood Count results
- `sample-chemistry-results.astm` - Basic metabolic panel
- `sample-order-query.astm` - Order request message

## Deployment

### As Windows Service

Use tools like NSSM or WinSW to install as a Windows service:

```bash
# Example with NSSM
nssm install "ASTM Interface Service" "C:\Program Files\Java\jdk1.8.0_XXX\bin\java.exe"
nssm set "ASTM Interface Service" AppParameters "-jar C:\path\to\astm-server-1.0.0.jar"
nssm set "ASTM Interface Service" AppDirectory "C:\path\to"
nssm start "ASTM Interface Service"
```

### Docker Deployment

```dockerfile
FROM openjdk:8-jre-alpine
COPY astm-server/target/astm-server-1.0.0.jar app.jar
EXPOSE 8080 9001 9002
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## Monitoring and Logging

### Logging Configuration

Logs are configured to output to both console and file:
- Log level: INFO for application, WARN for frameworks
- File rotation: 10MB max size, 30 day retention
- Format: Timestamp, thread, level, logger, message

### Health Endpoints

Spring Boot Actuator endpoints available:
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

## Troubleshooting

### Common Issues

1. **Port Already in Use**:
   - Check if another service is using the configured port
   - Use `netstat -an | findstr :9001` to check port usage

2. **Connection Timeouts**:
   - Verify network connectivity between instrument and server
   - Check firewall settings for the configured ports

3. **Message Parsing Errors**:
   - Enable DEBUG logging for `com.lis.astm` package
   - Verify ASTM message format using the simulator

4. **RabbitMQ Connection Issues**:
   - Verify RabbitMQ server is running
   - Check connection credentials in application.yml
   - Ensure queue permissions are properly configured

### Debug Mode

Enable detailed logging by setting log level to DEBUG:

```yaml
logging:
  level:
    com.lis.astm: DEBUG
```

## Contributing

### Code Style

- Follow standard Java naming conventions
- Use SLF4J for logging
- Include comprehensive JavaDoc comments
- Write unit tests for new functionality

### Adding New Features

1. Fork the repository
2. Create a feature branch
3. Implement changes with tests
4. Submit pull request with detailed description

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For technical support or questions:
- Review the troubleshooting section
- Check application logs for error details
- Verify configuration against sample provided
- Test with the included instrument simulator
