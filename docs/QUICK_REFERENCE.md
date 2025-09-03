# ASTM Interface Service - Developer Quick Reference

## 🚀 Quick Start Commands

```bash
# Build entire project
mvn clean install

# Run server
cd astm-server && mvn spring-boot:run

# Run simulator
cd instrument-simulator && java -jar target/instrument-simulator-1.0.0-shaded.jar
```

## 📁 Project Structure at a Glance

```
astm-interface-service/
├── astm-model/                    # 📦 Shared data models (POJOs)
│   └── src/main/java/com/lis/astm/model/
│       ├── AstmMessage.java       # Main message container
│       ├── HeaderRecord.java      # H record (message metadata)
│       ├── PatientRecord.java     # P record (demographics)
│       ├── OrderRecord.java       # O record (test orders)
│       ├── ResultRecord.java      # R record (test results)
│       └── TerminatorRecord.java  # L record (end marker)
│
├── astm-server/                   # 🖥️ Main server application
│   └── src/main/java/com/lis/astm/server/
│       ├── AstmInterfaceApplication.java      # Main Spring Boot class
│       ├── config/
│       │   └── AppConfig.java                 # Configuration from YAML
│       ├── core/
│       │   ├── ASTMServer.java                # TCP server manager
│       │   └── InstrumentConnectionHandler.java # Individual connections
│       ├── driver/
│       │   ├── InstrumentDriver.java          # Driver interface
│       │   └── impl/OrthoVisionDriver.java    # Example implementation
│       ├── messaging/
│       │   ├── ResultQueuePublisher.java     # Publish results → LIS
│       │   └── OrderQueueListener.java       # Listen for orders ← LIS
│       └── protocol/
│           ├── ASTMProtocolStateMachine.java  # Low-level protocol
│           └── ChecksumUtils.java             # ASTM checksums
│
└── instrument-simulator/          # 🧪 Testing tool
    └── src/main/java/com/lis/astm/simulator/
        └── InstrumentSimulator.java           # Interactive test client
```

## 🔧 Key Components Explained

### Configuration (application.yml)
```yaml
lis:
  instruments:
    - name: OrthoVision              # Instrument identifier
      port: 9001                    # TCP listen port
      driverClassName: com.lis...   # Driver implementation class
      enabled: true                 # Enable/disable
      maxConnections: 5             # Connection limit
  messaging:
    enabled: true                   # RabbitMQ integration
    orderQueueName: lis.orders.outbound
    resultQueueName: lis.results.inbound
```

### Core Classes Responsibilities

| Class | Purpose | Key Methods |
|-------|---------|-------------|
| `ASTMServer` | TCP server orchestrator | `startServer()`, `getConnectionHandler()` |
| `InstrumentConnectionHandler` | Individual connection manager | `run()`, `sendMessage()`, `handleIncomingMessages()` |
| `ASTMProtocolStateMachine` | ASTM protocol implementation | `sendMessage()`, `receiveMessage()` |
| `InstrumentDriver` | Parse/build ASTM messages | `parse()`, `build()` |
| `ResultQueuePublisher` | Publish to message queue | `publishResult()` |
| `OrderQueueListener` | Listen from message queue | `handleOrderMessage()` |

## 🔍 ASTM Protocol Flow

```
1. Instrument connects to TCP port
2. Instrument sends ENQ (enquiry)
3. Server responds with ACK (acknowledge)
4. Instrument sends framed message:
   STX + frame# + data + ETX/ETB + checksum + CR + LF
5. Server validates checksum, sends ACK/NAK
6. Repeat for multiple frames
7. Instrument sends EOT (end of transmission)
8. Connection ready for next message
```

## 🏗️ Adding New Instrument Driver

### 1. Create Driver Class
```java
@Component
public class MyInstrumentDriver implements InstrumentDriver {
    
    @Override
    public AstmMessage parse(String rawMessage) throws Exception {
        // Parse instrument-specific ASTM format
        AstmMessage message = new AstmMessage();
        
        // Split message into records
        String[] lines = rawMessage.split("\\r?\\n");
        for (String line : lines) {
            // Parse each record type (H, P, O, R, L)
            parseRecord(line, message);
        }
        
        return message;
    }
    
    @Override
    public String build(AstmMessage message) throws Exception {
        // Build instrument-specific ASTM format
        StringBuilder sb = new StringBuilder();
        
        if (message.getHeaderRecord() != null) {
            sb.append(buildHeaderRecord(message.getHeaderRecord())).append("\r\n");
        }
        // ... build other records
        
        return sb.toString();
    }
    
    @Override
    public String getInstrumentName() { return "MyInstrument"; }
    
    @Override
    public String getAstmVersion() { return "1394-97"; }
    
    @Override
    public boolean supportsMessage(String rawMessage) {
        return rawMessage.contains("MyInstrument");
    }
    
    @Override
    public String getDriverConfiguration() {
        return "{ \"instrumentName\": \"MyInstrument\" }";
    }
}
```

### 2. Add Configuration
```yaml
lis:
  instruments:
    - name: MyInstrument
      port: 9003
      driverClassName: com.lis.astm.server.driver.impl.MyInstrumentDriver
      enabled: true
```

## 🧪 Testing Workflow

### Using the Simulator
```bash
# Start simulator
java -jar instrument-simulator-1.0.0-shaded.jar

# Menu options:
1. Send Sample Results    # Pre-built CBC results
2. Send Custom Message   # Manual ASTM input
3. Send from File        # Load .astm files
4. Listen for Orders     # Receive from server
5. Test Connection       # Verify connectivity
```

### Sample ASTM Message Format
```
H|\^&|||OrthoVision|||||||P|1394-97|20250808120000
P|1|PAT001||ALT001|Doe^John^M||19800115|M
O|1|SPEC001||CBC^Complete Blood Count|R|20250808120000
R|1|WBC^White Blood Cell Count|7.5|K/uL|4.0-11.0|N||F|20250808120000
R|2|RBC^Red Blood Cell Count|4.2|M/uL|4.0-5.5|N||F|20250808120000
L|1|N
```

## 🐞 Common Debugging Scenarios

### Connection Issues
```bash
# Check if port is in use
netstat -an | findstr :9001

# Enable debug logging
logging.level.com.lis.astm: DEBUG
```

### Message Parsing Errors
```java
// Add detailed logging in driver
logger.debug("Parsing line: {}", line);
logger.debug("Extracted fields: {}", Arrays.toString(fields));
```

### Protocol Issues
```java
// ChecksumUtils debugging
String calculated = ChecksumUtils.calculateFrameChecksum(frameData);
String received = frame.substring(frame.length() - 4, frame.length() - 2);
logger.debug("Checksum - calculated: {}, received: {}", calculated, received);
```

## 📊 Message Queue Integration

### Publishing Results (Instrument → LIS)
```java
// Automatic publishing in InstrumentConnectionHandler
if (parsedMessage.hasResults()) {
    resultPublisher.publishResult(parsedMessage);
}
```

### Receiving Orders (LIS → Instrument)
```java
// Automatic routing in OrderQueueListener
@RabbitListener(queues = "${lis.messaging.orderQueueName}")
public void handleOrderMessage(String orderMessage) {
    AstmMessage order = objectMapper.readValue(orderMessage, AstmMessage.class);
    processOrder(order);
}
```

## ⚡ Performance Tips

1. **Connection Pooling**: Set appropriate `maxConnections` per instrument
2. **Thread Tuning**: Monitor thread pool usage under load
3. **Memory Management**: Large messages are automatically framed
4. **Error Recovery**: Failed connections auto-reconnect
5. **Monitoring**: Use Spring Actuator endpoints for health checks

## 🚨 Error Handling Patterns

### Graceful Degradation
```java
try {
    parseRecord(line, astmMessage);
} catch (Exception e) {
    logger.warn("Skipping invalid record: {}", line);
    // Continue processing other records
}
```

### Resource Cleanup
```java
try {
    // Process message
} finally {
    // Always cleanup
    if (socket != null) socket.close();
}
```

### Circuit Breaker Pattern
```java
if (consecutiveErrors > maxErrors) {
    logger.error("Too many errors, disabling connection");
    stop();
}
```

## 📈 Production Deployment

### As Windows Service
```bash
# Using NSSM (Non-Sucking Service Manager)
nssm install "ASTM Interface Service" "java.exe"
nssm set "ASTM Interface Service" AppParameters "-jar astm-server-1.0.0.jar"
nssm start "ASTM Interface Service"
```

### Environment Variables
```bash
# Production settings
export SPRING_PROFILES_ACTIVE=production
export RABBITMQ_HOST=prod-rabbitmq.company.com
export LOG_LEVEL=INFO
```

### Monitoring
```yaml
# Enable actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
```

## 🔐 Security Considerations

1. **Network Security**: Use VPN/firewall for instrument connections
2. **Authentication**: Consider adding basic auth for sensitive environments
3. **Encryption**: TLS for message queue connections in production
4. **Audit Trail**: Comprehensive logging for compliance
5. **Access Control**: Restrict actuator endpoints

## 📚 Further Reading

- [ASTM E1381 Standard](https://www.astm.org/Standards/E1381.htm)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/getstarted.html)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
