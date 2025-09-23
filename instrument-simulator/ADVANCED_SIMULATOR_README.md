# Advanced ASTM Simulator with Message Templates

## Overview
This enhanced ASTM simulator supports multiple message types through file-based templates and provides an interactive menu system for testing various ASTM scenarios.

## Features
- **Multiple Message Types**: Keep-Alive, Query, Result, Order, Error messages
- **File-Based Templates**: Store and modify test messages in separate files
- **Dynamic Variables**: Automatic timestamp, patient ID, specimen ID generation
- **Interactive Menu**: Easy selection of message types
- **Batch Testing**: Send all message types in sequence
- **Repeated Sending**: Continuous keep-alive or other message loops
- **Custom Messages**: Load and send any ASTM message from a file

## Usage

### Compile and Run
```bash
cd instrument-simulator
mvn compile
java -cp target/classes com.lis.astm.simulator.AdvancedAstmSimulator [host] [port]
```

### Default Connection
- Host: localhost
- Port: 9001

### Interactive Menu Options
1. **Send Keep-Alive Message** - Basic connection test
2. **Send Query Message** - Patient/specimen lookup
3. **Send Result Message** - Laboratory test results
4. **Send Order Message** - Test order requests
5. **Send Error Message** - Error condition simulation
6. **Send Custom Message** - Load message from any file
7. **Batch Test** - Send all message types sequentially
8. **Repeated Send** - Continuous keep-alive loop
9. **Exit** - Close simulator

## Message Templates

### Template Files Location
```
src/main/resources/test-messages/
├── keep-alive.astm      # Basic keep-alive message
├── query-message.astm   # Patient query message
├── result-message.astm  # Lab result message
├── order-message.astm   # Test order message
└── error-message.astm   # Error message
```

### Dynamic Variables
The simulator supports automatic variable substitution:

- `${timestamp}` - Current timestamp (yyyyMMddHHmmss)
- `${date}` - Current date (yyyyMMdd)
- `${time}` - Current time (HHmmss)
- `${patientId}` - Random patient ID (P123456)
- `${specimenId}` - Random specimen ID (S12345678)
- `${messageId}` - Random message ID (M1234567890)
- `${wbcValue}` - Random WBC value (4.0-11.0)
- `${rbcValue}` - Random RBC value (4.5-5.5)
- `${instrumentId}` - Instrument identification

### Example Template (result-message.astm)
```astm
H|\^&|||OCD^VISION^5.14.0^Simulator|||||||P|LIS2-A|${timestamp}
P|1|${patientId}||TESTPATIENT^JOHN^DOE||19850315|M|||123 Main St^City^State^12345|||
O|1|${specimenId}||^^^CBC^|R||||||A||||
R|1|^^^WBC^|${wbcValue}|10^3/uL|4.0-11.0||||F||||
R|2|^^^RBC^|${rbcValue}|10^6/uL|4.5-5.5||||F||||
L|1|N||
```

## Adding Your Own Test Messages

### Method 1: Edit Template Files
1. Modify existing `.astm` files in `src/main/resources/test-messages/`
2. Use dynamic variables for changing data
3. Restart simulator to reload templates

### Method 2: Custom Message Files
1. Create your ASTM message in any text file
2. Use option 6 "Send Custom Message" from menu
3. Enter path to your message file

### Method 3: Add New Template Type
1. Add your message template to `getDefault___Template()` method
2. Add corresponding case in main menu
3. Recompile simulator

## Message Format Notes
- Records should be separated by `\r` (carriage return)
- Each line represents one ASTM record (H|, P|, O|, R|, L|)
- The simulator automatically handles frame creation, checksums, and protocol
- No need to add STX, ETX, frame numbers, or checksums manually

## Testing Different Scenarios

### Basic Connection Test
```
Option 1: Send Keep-Alive Message
```

### Patient Query Workflow
```
Option 2: Send Query Message
```

### Lab Results Workflow
```
Option 3: Send Result Message
```

### Order Management
```
Option 4: Send Order Message
```

### Error Handling
```
Option 5: Send Error Message
```

### Comprehensive Testing
```
Option 7: Batch Test (All Messages)
```

### Long-Running Tests
```
Option 8: Repeated Send (Configure interval and count)
```

## Differences from SimpleKeepAliveSimulator
- **SimpleKeepAliveSimulator**: Single-purpose, automatic keep-alive only
- **AdvancedAstmSimulator**: Multi-purpose, interactive, template-based

Both simulators can be used simultaneously for different testing needs.
