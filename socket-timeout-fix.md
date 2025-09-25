# Socket Timeout Fix - Connection Closing Issue

## Problem
The simulator was connecting but closing immediately instead of waiting for the configured `READ_TIMEOUT_MS` (5 minutes / 300,000ms) from `ASTMServer.java`.

## Root Cause
In our previous non-blocking I/O implementation, we were overriding the socket timeout with a very short timeout (100ms) in `ASTMProtocolStateMachine.receiveMessage()`:

```java
socket.setSoTimeout(100); // This was overriding the 5-minute timeout!
```

## Solution Applied

### 1. Removed Socket Timeout Override
- **File**: `ASTMProtocolStateMachine.java`
- **Change**: Removed the `socket.setSoTimeout(100)` call
- **Result**: Now preserves the original `READ_TIMEOUT_MS` (5 minutes) set in `ASTMServer.java`

### 2. True Non-Blocking Implementation
- **Method**: `doReceiveMessageNonBlocking()`
- **Approach**: Uses `inputStream.available()` to check for data without blocking
- **Behavior**: 
  - If data is available → reads it normally (respects 5-minute timeout)
  - If no data → returns null immediately (non-blocking)

### 3. Proper Timeout Handling
- **Socket Timeout**: Now correctly uses `READ_TIMEOUT_MS` (300,000ms = 5 minutes)
- **Loop Delay**: Maintains `Thread.sleep(100)` to prevent CPU spinning
- **Connection Persistence**: Connections stay alive for 5 minutes as intended

## Expected Behavior After Fix

1. **Connection Duration**: Simulator connections will stay alive for 5 minutes (as configured)
2. **Non-Blocking**: Server can still handle both incoming and outgoing messages
3. **CPU Efficient**: No excessive CPU usage from tight loops
4. **Message Processing**: Orders will be queued and sent properly without blocking

## Testing Instructions

1. Start the ASTM server
2. Connect the simulator (should stay connected for ~5 minutes)
3. Use simulator option 12 to listen for orders
4. Send orders from the web interface
5. Verify:
   - Connection stays alive for 5 minutes
   - Orders are received by the simulator
   - No "unexpected character" errors
   - Proper ENQ/ACK communication

## Configuration Reference

In `ASTMServer.java`:
```java
private static final int READ_TIMEOUT_MS = 300_000; // 5 minutes
```

This timeout is now properly preserved throughout the connection lifecycle.
