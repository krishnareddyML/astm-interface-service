# ASTM Interface Service - Complete Laboratory Integration Solution

## 🔬 Overview

This is a **production-grade, enterprise-ready ASTM E1381/E1394 Interface Service** designed for reliable bidirectional communication between laboratory instruments and Laboratory Information Systems (LIS). The service provides a complete solution for ASTM protocol implementation with comprehensive model support, server capabilities, testing tools, and deployment options.

## 📋 Table of Contents

- [Project Structure](#project-structure)
- [Module Architecture](#module-architecture)
- [ASTM Protocol Support](#astm-protocol-support)
- [Quick Start Guide](#quick-start-guide)
- [Development Environment](#development-environment)
- [Production Deployment](#production-deployment)
- [Windows Service Installation](#windows-service-installation)
- [Database Configuration](#database-configuration)
- [Testing & Validation](#testing--validation)
- [Monitoring & Maintenance](#monitoring--maintenance)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting](#troubleshooting)

## 🏗️ Project Structure

```
astm-interface-service/
├── 📦 astm-model/                 # Core ASTM message models & data structures
├── 🌐 astm-server/               # Spring Boot service (main deployable)  
├── 🖥️  astm-parser-swing/        # GUI testing tool for ASTM messages
├── 🔧 instrument-simulator/       # Network simulator for testing
├── 📄 pom.xml                    # Parent Maven configuration
└── 📖 README.md                  # This comprehensive guide
```

## 🧩 Module Architecture

### 1. **astm-model** 📦
**Core Data Models & Message Structures**
- **Purpose**: Shared ASTM E1394 compliant data models
- **Key Components**:
  - `AstmMessage` - Complete message container
  - `HeaderRecord` (H) - Message headers and session info
  - `PatientRecord` (P) - Patient demographics
  - `OrderRecord` (O) - Test orders and specimen data  
  - `ResultRecord` (R) - Test results and analysis
  - `QueryRecord` (Q) - Information requests
  - `MResultRecord` (M) - ORTHO Vision specific results
  - `TerminatorRecord` (L) - Message termination
- **Features**:
  - Jackson JSON serialization support
  - ASTM field validation
  - Lombok-based clean code generation
  - Composite field parsing utilities

### 2. **astm-server** 🌐
**Main Spring Boot Service (Production Deployment)**
- **Purpose**: Core ASTM interface service for production use
- **Architecture**: Thread-safe, production-hardened TCP server
- **Key Features**:
  - **Multi-instrument Support**: Concurrent connections per instrument
  - **Database Integration**: SQL Server with message persistence
  - **Message Queuing**: RabbitMQ integration for LIS communication
  - **REST APIs**: Monitoring and management endpoints
  - **Spring Profiles**: Development, staging, and production configs
  - **Windows Service Ready**: Can be deployed as Windows service

#### Core Components:
- `ASTMServer` - Main TCP server with connection management
- `InstrumentConnectionHandler` - Per-connection message processing
- `ASTMProtocolStateMachine` - Thread-safe protocol implementation
- `InstrumentDriver` interface - Pluggable instrument-specific drivers
- Repository layer for database persistence
- Service layer for business logic

### 3. **astm-parser-swing** 🖥️
**Desktop GUI Testing Tool**
- **Purpose**: Visual ASTM message parsing and analysis
- **Technology**: Java Swing desktop application
- **Features**:
  - Paste raw ASTM messages for parsing
  - Tabbed view of all record types
  - Field-by-field analysis with ASTM specification details
  - Visual validation of message structure
  - Export capabilities for testing documentation

### 4. **instrument-simulator** 🔧  
**Network Testing Simulator**
- **Purpose**: Simulate laboratory instruments for testing
- **Features**:
  - JSON-driven test case definitions
  - Dynamic menu generation from test cases
  - Multi-frame message transmission with ETB/ETX
  - Order reception from server
  - Keep-alive message simulation
  - Network timeout and error simulation

## 🧬 ASTM Protocol Support

### Supported Instruments
| Instrument | Driver | Default port | Notes |
|---|---|---|---|
| ORTHO VISION (blood bank) | `com.lis.astm.server.driver.impl.OrthoVisionDriver` | 9001 | Results with M (well) records, queries, orders. Tested against the analyzer. |
| Sysmex XN series (hematology) | `com.lis.astm.server.driver.impl.SysmexXnDriver` | 5000 | XN ASTM Host Interface Spec (NA) rev. 11. See [docs/sysmex-xn-integration.md](docs/sysmex-xn-integration.md). Bench test pending. |

Each instrument is one entry under `lis.instruments` with its own TCP port, driver class and RabbitMQ queues.

### Supported Record Types
| Record | Type | Description | Implementation Status |
|--------|------|-------------|---------------------|
| **H** | Header | Session establishment & identification | ✅ Complete |
| **P** | Patient | Demographics & identification | ✅ Complete |
| **O** | Order | Test orders & specimen information | ✅ Complete |
| **R** | Result | Test results & analysis data | ✅ Complete |
| **Q** | Query | Information requests | ✅ Complete |
| **C** | Comment | Patient / sample / rerun-reflex comments (Sysmex XN) | ✅ Complete |
| **M** | M-Result | ORTHO Vision specific results | ✅ Complete |
| **S** | Scientific | Reagent information (Sysmex XN, ignored) | ℹ️ Logged only |
| **L** | Terminator | Message completion | ✅ Complete |

### Protocol Features
- ✅ **ASTM E1381-02 data link** over TCP: ENQ/ACK/NAK/EOT, frame numbers 1..7,0, 8-bit checksum
- ✅ **Multi-frame records** with ETB/ETX handling, duplicate-frame tolerance, 6 retransmissions on NAK
- ✅ **Spec timing**: 15 s sender timer, 30 s receiver timer, 20 s back-off after ENQ contention
- ✅ **Extended characters** (ISO-8859-1) in names survive checksum validation
- ✅ **Bidirectional communication** (LIS ↔ Instrument) with outbound orders sent within 500 ms of queueing
- ✅ **Session replacement**: a reconnecting analyzer replaces its stale session instead of being refused
- ✅ **Durable orders**: every order is persisted and ends as SUCCESS, retried, or FAILED (never stuck)
- ✅ **Automated tests** for framing, state machine, connection handler and both drivers (`mvn test`)

## 🚀 Quick Start Guide

### Prerequisites
```bash
# Required Software
Java 8
Maven 3.6 or higher
SQL Server 2019+ (for production)
Git for version control

# Optional for full features
RabbitMQ 3.8+ (for message queuing)
Visual Studio Code (recommended IDE)
```

### 1. Build the Complete Project
```powershell
# Clone and build all modules
git clone <repository-url>
cd astm-interface-service

# Build all modules (creates executable JARs)
mvn clean package

# Verify build artifacts
ls -la */target/*.jar
```

### 2. Quick Test with Simulator
```powershell
# Terminal 1: Start ASTM Server (local profile)
cd astm-server
mvn spring-boot:run

# Terminal 2: Start Instrument Simulator  
cd instrument-simulator
java -jar target/instrument-simulator.jar

# Terminal 3: Test ASTM Message Parser
cd astm-parser-swing
mvn exec:exec
```

### 3. Test ASTM Message Flow
```powershell
# In simulator, select option 2: "Send Full Result Message"
# Server will receive, parse, and log the message
# Check server logs for processing confirmation
```

## 🛠️ Development Environment

### IDE Setup (VS Code Recommended)
```json
// .vscode/settings.json
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.compile.nullAnalysis.mode": "automatic",
    "spring-boot.ls.problem.application-properties.enabled": true,
    "java.format.settings.url": "eclipse-formatter.xml"
}
```

### Local Development Configuration

#### Database Setup (Development)
```sql
-- Create database
CREATE DATABASE [ASTM_Interface_Dev]
GO

-- Create tables (auto-created by application)
USE [ASTM_Interface_Dev]
GO
```

#### Application Properties
Secrets are **not** stored in the repository. Set them in the environment (or in
`./config/application-local.yml` next to the jar, which Spring Boot loads automatically):

```powershell
set DB_PASSWORD=...            # required in every profile
set DB_HOST=192.168.20.123     # optional overrides: DB_NAME, DB_USERNAME
set RABBITMQ_PASSWORD=...      # RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USERNAME
```

```yaml
# astm-server/src/main/resources/application-local.yml (excerpt)
lis:
  instruments:
    - name: OrthoVision
      port: 9001
      driverClassName: com.lis.astm.server.driver.impl.OrthoVisionDriver
      enabled: true
      orderQueueName: ortho-vision.orders.queue
      resultQueueName: ortho-vision.results.queue
      resultsRoutingKey: result.inbound
      exchangeName: ortho-vision-exchange
    - name: SysmexXN
      port: 5000
      driverClassName: com.lis.astm.server.driver.impl.SysmexXnDriver
      enabled: true
      orderQueueName: sysmex-xn.orders.queue
      resultQueueName: sysmex-xn.results.queue
      resultsRoutingKey: result.inbound
      exchangeName: sysmex-xn-exchange
  messaging:
    enabled: true
```

Profile YAML lists are replaced, not merged, so every profile must list each instrument in full
(name, port, driverClassName and queue names). A listener is not started for an instrument that has
no `driverClassName`.

### Running Development Services
```powershell
# Start each module for development

# 1. ASTM Server (main service)
cd astm-server
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2. Instrument Simulator (testing)
#    args: [host] [port] [test-cases]  where test-cases is a bundled resource or a file path
java -jar instrument-simulator/target/instrument-simulator.jar localhost 9001 ortho-vision-test-cases.json
java -jar instrument-simulator/target/instrument-simulator.jar localhost 5000 sysmex-xn-test-cases.json

# 3. ASTM Parser GUI (message analysis)
cd astm-parser-swing
mvn exec:java -Dexec.mainClass="com.lis.astm.swing.AstmParserSwingApp"
```

## 🏭 Production Deployment

### Build Production Artifacts
```powershell
# Build with production profile
mvn clean package -Pprod

# Create deployable WAR (embedded Tomcat)
cd astm-server
mvn spring-boot:repackage

# Artifacts created:
# astm-server/target/astm-server-1.0.0.jar (executable JAR)
# instrument-simulator/target/instrument-simulator.jar  
```

### Production Configuration

#### Environment Variables
```powershell
# Required production environment variables (no defaults exist in application-prod.yml)
set SPRING_PROFILES_ACTIVE=prod
set DB_HOST=prod-sql-server.company.com
set DB_NAME=ASTM_Interface_Prod
set DB_USERNAME=astm_service_user
set DB_PASSWORD=<secret>
set RABBITMQ_HOST=prod-rabbitmq.company.com
set RABBITMQ_USERNAME=astm_queue_user
set RABBITMQ_PASSWORD=<secret>
set SERVER_PORT=8080

# Optional per-instrument switches
set ORTHO_VISION_ENABLED=true
set ORTHO_VISION_PORT=9001
set SYSMEX_XN_ENABLED=true
set SYSMEX_XN_PORT=5000
set LOG_PATH=D:\astm-interface-services-data\logs
```

#### Production application.yml
```yaml
# astm-server/src/main/resources/application-prod.yml
spring:
  datasource:
    url: jdbc:sqlserver://${DB_HOST}:1433;databaseName=ASTM_Interface_Prod;encrypt=true
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000

lis:
  instruments:
    - name: OrthoVision
      port: ${ORTHO_VISION_PORT:9001}
      driverClassName: com.lis.astm.server.driver.impl.OrthoVisionDriver
      enabled: ${ORTHO_VISION_ENABLED:true}
      orderQueueName: ortho-vision.orders.queue
      resultQueueName: ortho-vision.results.queue
      resultsRoutingKey: result.inbound
      exchangeName: ortho-vision-exchange
    - name: SysmexXN
      port: ${SYSMEX_XN_PORT:5000}
      driverClassName: com.lis.astm.server.driver.impl.SysmexXnDriver
      enabled: ${SYSMEX_XN_ENABLED:false}
      orderQueueName: sysmex-xn.orders.queue
      resultQueueName: sysmex-xn.results.queue
      resultsRoutingKey: result.inbound
      exchangeName: sysmex-xn-exchange
  messaging:
    enabled: true
    retry:
      max-attempts: 10
      collision-delay-minutes: 30

server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /astm-interface
```

### Production Deployment Options

#### Option 1: Standalone JAR Deployment
```powershell
# Copy JAR to production server
copy astm-server\target\astm-server-1.0.0.jar D:\Applications\ASTM-Interface\

# Create application directory structure
mkdir D:\Applications\ASTM-Interface\config
mkdir D:\Applications\ASTM-Interface\logs

# Run as standalone application
cd D:\Applications\ASTM-Interface
java -jar -Xmx2g -Dspring.profiles.active=prod astm-server-1.0.0.jar
```

#### Option 2: Windows Service Deployment (Recommended)
```powershell
# Install as Windows Service using NSSM or similar
# See "Windows Service Installation" section below
```

## 🪟 Windows Service Installation

### Method 1: Using NSSM (Non-Sucking Service Manager)

#### Install NSSM
```powershell
# Download NSSM from https://nssm.cc/download
# Extract to C:\Tools\nssm\

# Add to system PATH
$env:Path += ";C:\Tools\nssm\win64"
```

#### Create Windows Service
```powershell
# Create service configuration
nssm install "ASTM Interface Service"

# Configure service parameters
nssm set "ASTM Interface Service" Application "C:\Program Files\Java\jdk-1.8\bin\java.exe"
nssm set "ASTM Interface Service" AppParameters "-jar D:\Applications\ASTM-Interface\astm-server-1.0.0.jar"
nssm set "ASTM Interface Service" AppDirectory "D:\Applications\ASTM-Interface"

# Set environment variables
nssm set "ASTM Interface Service" AppEnvironmentExtra "SPRING_PROFILES_ACTIVE=prod"
nssm set "ASTM Interface Service" AppEnvironmentExtra "DB_HOST=your-sql-server"
nssm set "ASTM Interface Service" AppEnvironmentExtra "DB_USERNAME=astm_user"
nssm set "ASTM Interface Service" AppEnvironmentExtra "DB_PASSWORD=your_password"

# Configure service properties
nssm set "ASTM Interface Service" DisplayName "ASTM Interface Service"  
nssm set "ASTM Interface Service" Description "Laboratory ASTM E1394 Interface Service"
nssm set "ASTM Interface Service" Start SERVICE_AUTO_START

# Set up logging
nssm set "ASTM Interface Service" AppStdout "D:\Applications\ASTM-Interface\logs\service-out.log"
nssm set "ASTM Interface Service" AppStderr "D:\Applications\ASTM-Interface\logs\service-err.log"
```

#### Service Management
```powershell
# Start service
net start "ASTM Interface Service"
# or
nssm start "ASTM Interface Service"

# Stop service  
net stop "ASTM Interface Service"
# or  
nssm stop "ASTM Interface Service"

# Check service status
sc query "ASTM Interface Service"

# View service configuration
nssm edit "ASTM Interface Service"
```

### Method 2: PowerShell Service Creation
```powershell
# Create service directly with PowerShell
$serviceName = "ASTMInterfaceService" 
$jarPath = "D:\Applications\ASTM-Interface\astm-server-1.0.0.jar"
$javaPath = "C:\Program Files\Java\jdk-1.8\bin\java.exe"
$workingDir = "D:\Applications\ASTM-Interface"

# Create Windows service
New-Service -Name $serviceName -BinaryPathName "`"$javaPath`" -jar `"$jarPath`"" -DisplayName "ASTM Interface Service" -StartupType Automatic -Description "Laboratory ASTM Interface Service"

# Set service to run in specific directory  
sc.exe config $serviceName binPath= "`"$javaPath`" -Dspring.profiles.active=prod -jar `"$jarPath`""
```

### Service Configuration File
Create `D:\Applications\ASTM-Interface\service.bat`:
```batch
@echo off
cd /d "D:\Applications\ASTM-Interface"
set SPRING_PROFILES_ACTIVE=prod
set DB_HOST=your-sql-server
set DB_USERNAME=astm_user  
set DB_PASSWORD=your_password

"C:\Program Files\Java\jdk-1.8\bin\java.exe" ^
    -Xmx2g ^
    -Dspring.profiles.active=prod ^
    -jar astm-server-1.0.0.jar
```

## 🗄️ Database Configuration

### SQL Server Setup

#### 1. Database Creation
```sql
-- Create database
CREATE DATABASE [ASTM_Interface_Prod]
ON (
    NAME = 'ASTM_Interface_Data',
    FILENAME = 'C:\ASTM_Data\ASTM_Interface_Prod.mdf',
    SIZE = 500MB,
    MAXSIZE = 10GB,
    FILEGROWTH = 50MB
)
LOG ON (
    NAME = 'ASTM_Interface_Log',  
    FILENAME = 'C:\ASTM_Data\ASTM_Interface_Prod_Log.ldf',
    SIZE = 100MB,
    MAXSIZE = 2GB,
    FILEGROWTH = 10MB
)
GO
```

#### 2. Service Account Setup
```sql
-- Create service login and user
USE [master]
GO
CREATE LOGIN [astm_service_user] WITH PASSWORD = 'SecurePassword123!', 
    DEFAULT_DATABASE = [ASTM_Interface_Prod],
    CHECK_EXPIRATION = OFF,
    CHECK_POLICY = OFF
GO

USE [ASTM_Interface_Prod]  
GO
CREATE USER [astm_service_user] FOR LOGIN [astm_service_user]
GO
ALTER ROLE [db_datareader] ADD MEMBER [astm_service_user]
ALTER ROLE [db_datawriter] ADD MEMBER [astm_service_user]
ALTER ROLE [db_ddladmin] ADD MEMBER [astm_service_user]
GO
```

#### 3. Tables (Auto-created by Application)
The application automatically creates these tables:
```sql
-- ASTMServerMessages - Incoming messages from instruments  
-- ASTMOrderMessages - Outgoing orders to instruments
-- (Schema managed by Spring Boot + JDBC)
```

#### 4. Connection String Examples
```yaml
# Local Development
spring.datasource.url: jdbc:sqlserver://localhost:1433;databaseName=ASTM_Interface_Dev;trustServerCertificate=true

# Production (with encryption)
spring.datasource.url: jdbc:sqlserver://prod-sql.company.com:1433;databaseName=ASTM_Interface_Prod;encrypt=true;trustServerCertificate=false

# Production (Azure SQL)  
spring.datasource.url: jdbc:sqlserver://astm-sql-server.database.windows.net:1433;databaseName=ASTM_Interface_Prod;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30
```

## 🧪 Testing & Validation

### Unit Testing
```powershell
# Run all unit tests (also run by GitHub Actions on every push and pull request)
mvn test

# Run specific module tests
cd astm-server
mvn test
```

What is covered:
- `ChecksumUtilsTest`: frame layout, ETB/ETX, checksum over extended characters
- `ASTMProtocolStateMachineTest`: receive/send over a real socket pair (split records, bad checksum, duplicate and out-of-sequence frames, ENQ contention, six NAKs)
- `InstrumentConnectionHandlerTest`: result publishing, keep-alive, order send success/failure/contention, queue hand-back on disconnect
- `SysmexXnDriverTest`: every example message from the XN specification, exact order-response output
- `OrthoVisionDriverTest`: captured analyzer messages and order building
- `AstmEscapeTest`: ASTM escape sequences

### Integration Testing

#### 1. ASTM Message Parser Testing
```powershell
# Launch GUI parser
cd astm-parser-swing  
mvn exec:java

# Test with sample ASTM message:
H|\^&|||OCD^VISION^5.14.0^VISION|||||||P|LIS2-A|20241025120000
P|1|PAT001||SMITH^JOHN^A^^MR|19900515000000|M||||||||DOC123
O|1|SID101||^^^ABO|||||||N||||CENTBLOOD  
R|1|^^^ABO^|A+|||||||||||20241025120000||VISION001
L|1|N
```

#### 2. Network Communication Testing  
```powershell
# Terminal 1: Start server
cd astm-server
mvn spring-boot:run -Dspring.profiles.active=local

# Terminal 2: Start simulator (Ortho Vision on 9001, or Sysmex XN cases on 5000)
java -jar instrument-simulator/target/instrument-simulator.jar localhost 9001
java -jar instrument-simulator/target/instrument-simulator.jar localhost 5000 sysmex-xn-test-cases.json

# In simulator:
# 1. Select "Send Full Result Message"  
# 2. Select "Listen for Orders from Server"
# 3. Test various message types and error conditions
```

#### 3. Load Testing
```powershell
# Multiple simulator instances
for /L %i in (1,1,10) do (
    start java -jar instrument-simulator.jar localhost 900%i
)
```

### Message Validation Testing

#### Valid ASTM Message Examples
```
# Basic Result Message
H|\^&|||Simulator|||||||P|LIS2-A|20241025120000\r
P|1|PAT001|||PATIENT^TEST|\r  
R|1|^^^WBC^|10.5|10^3/uL||N||F|||\r
L|1|N\r

# Order Message  
H|\^&|||LIS|||||||P|LIS2-A|20241025120000\r
P|1|PAT001||SMITH^JOHN^A^^MR|\r
O|1|SID101||ABO|||||||N||||CENTBLOOD\r  
L||\r
```

## 📊 Monitoring & Maintenance

### Application Monitoring

#### REST API Endpoints
```http
# Health check
GET http://localhost:8080/astm-interface/actuator/health

# Application info  
GET http://localhost:8080/astm-interface/actuator/info

# Metrics
GET http://localhost:8080/astm-interface/actuator/metrics

# Custom ASTM endpoints (when implemented)
GET http://localhost:8080/astm-interface/api/astm/status
GET http://localhost:8080/astm-interface/api/astm/connections  
GET http://localhost:8080/astm-interface/api/astm/statistics
```

#### Log Configuration
```yaml  
# logback-spring.xml configuration
logging:
  level:
    com.lis.astm: INFO
    org.springframework: WARN
    root: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: D:/Applications/ASTM-Interface/logs/astm-interface.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
      total-size-cap: 1GB
```

### Database Monitoring
```sql
-- Monitor message processing
SELECT Status, COUNT(*) as Count, MAX(ReceivedAt) as LastReceived
FROM ASTMServerMessages 
GROUP BY Status;

-- Monitor order queue
SELECT Status, COUNT(*) as Count, MAX(CreatedAt) as LastCreated  
FROM ASTMOrderMessages
GROUP BY Status;

-- Performance monitoring
SELECT 
    InstrumentName,
    COUNT(*) as MessageCount,
    AVG(DATEDIFF(ms, ReceivedAt, ProcessedAt)) as AvgProcessingTimeMs
FROM ASTMServerMessages 
WHERE ProcessedAt IS NOT NULL
    AND ReceivedAt > DATEADD(hour, -24, GETDATE())
GROUP BY InstrumentName;
```

### System Health Checks
```powershell
# Windows Service Status
Get-Service "ASTM Interface Service"

# Process monitoring
Get-Process -Name java | Where-Object {$_.ProcessName -eq "java" -and $_.MainWindowTitle -like "*astm*"}

# Port connectivity
Test-NetConnection -ComputerName localhost -Port 9001
Test-NetConnection -ComputerName localhost -Port 8080

# Log file monitoring  
Get-Content -Path "D:\Applications\ASTM-Interface\logs\astm-interface.log" -Tail 50 -Wait
```

## ⚡ Performance Tuning

### JVM Configuration
```powershell
# Production JVM settings
java -Xmx2g ^
     -Xms1g ^
     -XX:+UseG1GC ^
     -XX:MaxGCPauseMillis=100 ^
     -XX:+UseStringDeduplication ^
     -Djava.awt.headless=true ^
     -Dspring.profiles.active=prod ^
     -jar astm-server-1.0.0.jar
```

### Database Performance  
```sql
-- Index recommendations
CREATE INDEX IX_ServerMessages_Status_ReceivedAt 
ON ASTMServerMessages(Status, ReceivedAt);

CREATE INDEX IX_OrderMessages_Status_NextRetryAt
ON ASTMOrderMessages(Status, NextRetryAt)  
WHERE Status IN ('PENDING', 'FAILED');

-- Maintenance tasks
-- Clean old successful messages (run weekly)
DELETE FROM ASTMServerMessages 
WHERE Status = 'PUBLISHED' 
  AND PublishedAt < DATEADD(day, -30, GETDATE());
```

### Connection Pool Tuning
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5  
      connection-timeout: 30000
      idle-timeout: 300000
      max-lifetime: 900000
      leak-detection-threshold: 60000
```

## 🔧 Troubleshooting

### Common Issues & Solutions

#### 1. Service Won't Start
```powershell  
# Check Java version
java -version

# Verify JAR integrity
java -jar astm-server-1.0.0.jar --version

# Check port availability
netstat -an | findstr :9001
netstat -an | findstr :8080

# Review service logs
type D:\Applications\ASTM-Interface\logs\service-err.log
```

#### 2. Database Connection Issues
```powershell
# Test connection string
sqlcmd -S server -d ASTM_Interface_Prod -U astm_service_user -P password

# Check firewall settings  
Test-NetConnection -ComputerName sql-server -Port 1433

# Verify credentials
SELECT SYSTEM_USER, USER_NAME();
```

#### 3. ASTM Communication Problems
```powershell
# Test instrument connectivity
telnet instrument-ip 9001

# Monitor network traffic
netstat -an | findstr :9001

# Check ASTM message format
# Use astm-parser-swing to validate messages
```

#### 4. Memory Issues
```powershell  
# Monitor JVM memory usage
jstat -gc [pid] 5s

# Heap dump analysis (if needed)
jcmd [pid] GC.run_finalization
jcmd [pid] VM.gc
```

### Log Analysis

#### Key Log Patterns to Monitor
```bash
# Successful message processing
grep "Successfully processed ASTM message" astm-interface.log

# Connection issues  
grep "Connection.*failed\|Connection.*refused" astm-interface.log

# Database errors
grep "SQLException\|DataAccessException" astm-interface.log

# Memory warnings
grep "OutOfMemoryError\|GC overhead limit" astm-interface.log
```

## 📚 Additional Resources

### ASTM Standards
- **ASTM E1381-02**: Standard Specification for Low-Level Protocol  
- **ASTM E1394-97**: Standard Specification for Transferring Information Between Clinical Laboratory Instruments

### Development Tools
- **Maven**: Build automation and dependency management
- **Spring Boot**: Application framework and configuration  
- **Lombok**: Code generation and boilerplate reduction
- **Jackson**: JSON serialization and data binding
- **HikariCP**: High-performance database connection pooling

### Production Tools  
- **NSSM**: Windows service management
- **SQL Server Management Studio**: Database administration
- **RabbitMQ Management**: Message queue monitoring
- **Java Mission Control**: JVM performance monitoring

---

## 📋 Deployment Checklist

### Pre-Deployment
- [ ] Build all artifacts with production profile
- [ ] Database server prepared with proper permissions  
- [ ] Environment variables configured
- [ ] Network ports opened (9001 for ASTM, 8080 for REST API)
- [ ] Java 1.8 installed on target server
- [ ] Service account created for Windows service

### Deployment
- [ ] Copy JAR files to production directory
- [ ] Create Windows service with NSSM
- [ ] Configure service environment variables
- [ ] Set up log rotation and monitoring
- [ ] Test service start/stop/restart
- [ ] Verify database connectivity
- [ ] Test ASTM instrument communication

### Post-Deployment
- [ ] Monitor service logs for first 24 hours
- [ ] Verify message processing statistics
- [ ] Test failover and recovery scenarios  
- [ ] Document any environment-specific configurations
- [ ] Schedule maintenance tasks (log cleanup, etc.)

---

**Version**: 1.0.0-PRODUCTION  
**Last Updated**: September 2025  
**Status**: ✅ Production Ready  
**Architecture**: Multi-module Maven project with Spring Boot  
**Deployment**: JAR + Windows Service  
**Database**: SQL Server with JDBC  
**Integration**: RabbitMQ message queuing