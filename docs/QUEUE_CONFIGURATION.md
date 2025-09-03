# Queue Configuration Guide

## 🎯 **Instrument-Specific Queue Configuration**

Your ASTM Interface Service now supports **two methods** for configuring outbound order queues:

### **Method 1: Automatic Queue Names (Default)**

```yaml
lis:
  instruments:
    - name: OrthoVision
      port: 9001
      # No orderQueueName specified - uses auto-generation
  
  messaging:
    orderQueuePrefix: lis.orders.outbound.  # Base prefix
```

**Results in:**
- Queue: `lis.orders.outbound.orthovision`

### **Method 2: Explicit Queue Names (Recommended)**

```yaml
lis:
  instruments:
    - name: OrthoVision
      port: 9001
      orderQueueName: lis.orders.outbound.ortho  # Explicit queue name
    
    - name: HematologyAnalyzer  
      port: 9002
      orderQueueName: lis.orders.outbound.hema   # Different explicit name
```

**Results in:**
- OrthoVision: `lis.orders.outbound.ortho`
- HematologyAnalyzer: `lis.orders.outbound.hema`

## 📊 **Complete Queue Architecture**

### **Outbound Queues (Orders: LIS → Instruments)**
```
lis.orders.outbound.ortho          ← Core LIS publishes OrthoVision orders
lis.orders.outbound.hema           ← Core LIS publishes Hematology orders  
lis.orders.outbound.chemistry      ← Core LIS publishes Chemistry orders
```

### **Inbound Queue (Results: Instruments → LIS)**
```
lis.results.inbound                ← All instruments publish results here
```

## 🔧 **Configuration Benefits**

### **Per-Instrument Order Queues:**
- ✅ **Isolation**: OrthoVision orders don't block Hematology processing
- ✅ **Monitoring**: Track order backlogs per instrument
- ✅ **Scaling**: Each instrument can have different processing rates
- ✅ **Maintenance**: Restart/purge queues independently

### **Shared Result Queue:**
- ✅ **Simplicity**: Core LIS has one place to consume results
- ✅ **Unified Processing**: All results processed by same logic
- ✅ **Easy Monitoring**: Single queue to watch for result volume

## 📝 **Core LIS Integration**

When the Core LIS publishes orders, it must specify the **target instrument queue**:

```java
// Core LIS publishes to specific instrument queue
rabbitTemplate.convertAndSend("lis.orders.outbound.ortho", orderMessage);
rabbitTemplate.convertAndSend("lis.orders.outbound.hema", orderMessage);
```

## 🧪 **Testing Without RabbitMQ**

Current configuration has messaging disabled for testing:

```yaml
lis:
  messaging:
    enabled: false  # Disabled for testing
```

**To test with RabbitMQ:**
1. Install RabbitMQ locally
2. Change `enabled: true`
3. Create the queues manually or let Spring create them automatically

## 🚀 **Production Deployment**

### **Queue Setup Script:**
```bash
# Create instrument-specific order queues
rabbitmqadmin declare queue name=lis.orders.outbound.ortho durable=true
rabbitmqadmin declare queue name=lis.orders.outbound.hema durable=true

# Create shared result queue  
rabbitmqadmin declare queue name=lis.results.inbound durable=true
```

### **Monitoring:**
```bash
# Check queue status
rabbitmqctl list_queues name messages consumers

# Example output:
# lis.orders.outbound.ortho    5    1
# lis.orders.outbound.hema     2    1  
# lis.results.inbound          0    1
```

This architecture provides **maximum flexibility** and **operational benefits** for your laboratory environment! 🎉
