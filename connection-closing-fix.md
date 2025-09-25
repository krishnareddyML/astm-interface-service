# Connection Closing Issue Fix - handleIncomingMessages() Method

## Problem Identified
The connection was closing immediately after the simulator connected because the `handleIncomingMessages()` method was incorrectly interpreting non-blocking behavior as a connection termination signal.

## Root Cause Analysis

### Original Logic (Problematic):
```java
if (rawMessage == null) {
    log.warn("No message received - connection timed out. Terminating connection handler.");
    return false; // This breaks the main loop and closes connection!
}
```

### The Issue:
1. **Non-blocking `receiveMessage()`** returns `null` when no data is immediately available
2. **`handleIncomingMessages()`** interpreted `null` as "connection closed"
3. **Main loop** receives `false` and terminates the connection handler
4. **Connection closes** immediately instead of waiting for the 5-minute timeout

## Fix Applied

### New Logic (Correct):
```java
if (rawMessage == null) {
    // Check if the socket is actually closed/disconnected
    if (!protocolStateMachine.isConnected()) {
        log.warn("Connection lost - socket closed. Terminating connection handler.");
        return false; // Only terminate if socket is actually closed
    }
    
    // No data available right now, but connection is still alive
    log.debug("No data immediately available - continuing to listen");
    return true; // Keep connection alive for non-blocking behavior
}
```

### Key Changes:
1. **Differentiate scenarios**: "No data right now" vs "Connection actually closed"
2. **Check socket state**: Use `protocolStateMachine.isConnected()` to verify actual connection status
3. **Return true for no data**: Keep the connection alive when no immediate data is available
4. **Return false only for real disconnects**: Only terminate when socket is actually closed

## Expected Behavior After Fix

### Before Fix:
- ❌ Simulator connects → `receiveMessage()` returns `null` → Connection closes immediately

### After Fix:
- ✅ Simulator connects → `receiveMessage()` returns `null` → Connection stays alive
- ✅ Main loop continues → Processes outgoing messages from queue
- ✅ Connection respects 5-minute `READ_TIMEOUT_MS` setting
- ✅ Only closes on actual socket disconnect or timeout

## Architecture Flow

```
[Simulator Connects] → [handleIncomingMessages()]
                          ↓
                     [receiveMessage() = null]
                          ↓
                     [Check socket.isConnected()]
                          ↓
                     [If connected: return true (keep alive)]
                     [If disconnected: return false (terminate)]
                          ↓
                     [Main loop continues/terminates accordingly]
```

## Testing Instructions

1. **Start ASTM server**
2. **Connect simulator** - should now stay connected
3. **Use option 12** - should wait for orders without closing
4. **Send order** - should be received properly
5. **Wait 5 minutes** - connection should timeout naturally (not immediately)

This fix ensures that the non-blocking I/O implementation works correctly while maintaining proper connection lifecycle management.
