# ASTM Keep-Alive Implementation

## Overview

The ASTM Interface Service now supports ASTM Keep-Alive messages to maintain TCP/IP connections between the LIS and ORTHO VISION® systems during periods of inactivity. This prevents connection drops and "Apsw26 Unable to Connect to the LIS" errors.

## Keep-Alive Protocol Flow

The keep-alive implementation follows the standard ASTM protocol:

```
VISION: <ENQ>
LIS:    <ACK>
VISION: <STX>1H|\^&|||OCD^VISION^5.14.0.47342^JNumber|||||||P|LIS2-A|20220902174004<CR><ETX>21<CR><LF>
LIS:    <ACK>
VISION: <STX>2L||<CR><ETX>86<CR><LF>
LIS:    <ACK>
VISION: <EOT>
```

## Configuration

Keep-alive is configured per instrument in the application YAML files:

```yaml
lis:
  instruments:
    - name: OrthoVision
      port: 9001
      enabled: true
      keepAliveIntervalMinutes: 60  # Send keep-alive every 60 minutes
    - name: HematologyAnalyzer
      port: 9002
      enabled: true
      keepAliveIntervalMinutes: 0   # Disabled (0 = no keep-alive)
```

### Configuration Options

- **keepAliveIntervalMinutes**: 
  - Range: 0-1440 minutes
  - 0 = Disabled (no keep-alive messages)
  - 1-1440 = Enabled with specified interval
  - Recommended: 30-120 minutes for production

### Environment-Specific Defaults

#### Local Development (`application-local.yml`)
- OrthoVision: 5 minutes (for testing)
- HematologyAnalyzer: Disabled

#### Development (`application-dev.yml`)
- OrthoVision: 30 minutes
- HematologyAnalyzer: 15 minutes

#### Production (`application-prod.yml`)
- OrthoVision: 60 minutes (recommended for VISION® systems)
- HematologyAnalyzer: 120 minutes

## Implementation Details

### Key Components

1. **AstmKeepAliveService**: Core keep-alive service
   - Manages scheduled keep-alive transmission
   - Handles incoming keep-alive messages
   - Provides statistics and monitoring

2. **InstrumentConnectionHandler**: Enhanced to support keep-alive
   - Integrates keep-alive service
   - Filters keep-alive messages from normal data flow
   - Manages keep-alive lifecycle with connection

3. **ASTMServer**: Updated to support keep-alive
   - Provides shared scheduler for all keep-alive services
   - Passes configuration to connection handlers

### Message Detection

Keep-alive messages are automatically detected by:
- Presence of Header (H) record
- Presence of Terminator (L) record with minimal data
- Absence of data records (P, O, R, Q, M)

### Error Handling

- Failed keep-alive transmission logs errors
- Connection issues during keep-alive are handled gracefully
- Keep-alive failures don't interrupt normal data flow
- Monitoring and statistics available for troubleshooting

## Monitoring

### Statistics Available

Each keep-alive service provides:
- Enabled status
- Configured interval
- Last keep-alive sent timestamp
- Last keep-alive received timestamp
- Current operation status

### Logging

Keep-alive operations are logged at appropriate levels:
- INFO: Service start/stop, configuration changes
- DEBUG: Individual keep-alive messages
- ERROR: Keep-alive failures and connection issues

## Benefits

1. **Connection Stability**: Prevents TCP connection drops during idle periods
2. **Error Prevention**: Eliminates "Apsw26 Unable to Connect to the LIS" errors
3. **Configurable**: Flexible per-instrument configuration
4. **Non-Intrusive**: Keep-alive messages don't interfere with normal operations
5. **Monitoring**: Full visibility into keep-alive operations

## Troubleshooting

### Common Issues

1. **Keep-alive not working**:
   - Check `keepAliveIntervalMinutes` > 0
   - Verify instrument connection is active
   - Check logs for keep-alive service start messages

2. **"Apsw26 Unable to Connect to the LIS" still occurring**:
   - Reduce keep-alive interval
   - Check network connectivity
   - Verify ASTM protocol compliance

3. **Performance impact**:
   - Keep-alive uses minimal resources
   - Consider increasing interval if too frequent
   - Monitor connection handler thread usage

### Log Analysis

```bash
# Check keep-alive service startup
grep "ASTM Keep-Alive Service" logs/astm-interface-service.log

# Monitor keep-alive messages
grep "keep-alive" logs/astm-interface-service.log

# Check for keep-alive failures
grep "Keep-alive failed" logs/astm-interface-service.log
```

## Best Practices

1. **Production Settings**:
   - Use 30-120 minute intervals for VISION® systems
   - Monitor connection stability after implementation
   - Adjust intervals based on network characteristics

2. **Development/Testing**:
   - Use shorter intervals (5-15 minutes) for testing
   - Enable debug logging to observe keep-alive flow
   - Test with actual VISION® hardware when possible

3. **Monitoring**:
   - Set up alerts for keep-alive failures
   - Monitor connection drop rates before/after implementation
   - Track "Apsw26" error frequency

## Future Enhancements

Potential improvements for future versions:
- Dynamic interval adjustment based on network conditions
- Keep-alive health check endpoints
- Integration with monitoring systems (Prometheus, etc.)
- Advanced failure recovery strategies
