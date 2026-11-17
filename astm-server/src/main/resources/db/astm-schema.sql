/*
  ASTM Interface Service - database schema (SQL Server)

  This service owns its own database, separate from the Core LIS databases. Only astm-server
  reads or writes these tables; the Core LIS receives everything through RabbitMQ.

  Database per environment (the profile default, override with DB_NAME):
    local / dev  infinity-lis-astm-dev
    staging      infinity-lis-astm-stage
    prod         infinity-lis-astm-prod

  Run this script in the ASTM database (it is safe to run again; existing objects are kept):
    sqlcmd -S <server> -d infinity-lis-astm-dev -i astm-schema.sql
  The application never creates or alters tables itself.

  Tables (direction is relative to the LIS, as in the RabbitMQ names lis.inbound.x / lis.outbound.x):
    AnalyzerInboundMessages  every message an analyzer sends (results, QC, queries): raw text,
                             parse outcome and whether it was published to RabbitMQ.
                             Status: RECEIVED, PROCESSED, PUBLISHED, PUBLISH_RETRY, ERROR
    AnalyzerOutboundOrders   every order taken from RabbitMQ for an analyzer, until it is
                             delivered or given up after the retries.
                             Status: PENDING, PROCESSING, SUCCESS, FAILED

  Columns must match the repositories (ServerMessageRepository, OrderMessageRepository);
  SchemaScriptTest fails the build when they drift apart.
*/

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.AnalyzerInboundMessages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AnalyzerInboundMessages (
        Id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_AnalyzerInboundMessages PRIMARY KEY,
        MessageId      NVARCHAR(64)   NOT NULL,   -- UUID assigned on receipt
        InstrumentName NVARCHAR(100)  NOT NULL,   -- lis.instruments[].name
        RawMessage     NVARCHAR(MAX)  NOT NULL,   -- records as received, CR LF separated
        MessageType    NVARCHAR(50)   NULL,       -- RESULT, QC_RESULT, QUERY, KEEP_ALIVE ...
        Status         NVARCHAR(20)   NOT NULL,
        ReceivedAt     DATETIME2(3)   NOT NULL,
        ProcessedAt    DATETIME2(3)   NULL,
        PublishedAt    DATETIME2(3)   NULL,
        ErrorMessage   NVARCHAR(MAX)  NULL,
        RemoteAddress  NVARCHAR(100)  NULL,       -- analyzer IP:port
        CONSTRAINT UQ_AnalyzerInboundMessages_MessageId UNIQUE (MessageId),
        CONSTRAINT CK_AnalyzerInboundMessages_Status
            CHECK (Status IN ('RECEIVED', 'PROCESSED', 'PUBLISHED', 'PUBLISH_RETRY', 'ERROR'))
    );
    -- retry job and monitoring (findByStatus), cleanup of old PUBLISHED rows, recent per analyzer
    CREATE INDEX IX_AnalyzerInboundMessages_Status_ReceivedAt
        ON dbo.AnalyzerInboundMessages (Status, ReceivedAt);
    CREATE INDEX IX_AnalyzerInboundMessages_Status_PublishedAt
        ON dbo.AnalyzerInboundMessages (Status, PublishedAt);
    CREATE INDEX IX_AnalyzerInboundMessages_Instrument_ReceivedAt
        ON dbo.AnalyzerInboundMessages (InstrumentName, ReceivedAt);
END
GO

IF OBJECT_ID(N'dbo.AnalyzerOutboundOrders', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AnalyzerOutboundOrders (
        Id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_AnalyzerOutboundOrders PRIMARY KEY,
        MessageId        NVARCHAR(64)   NOT NULL,   -- UUID assigned when consumed from RabbitMQ
        InstrumentName   NVARCHAR(100)  NOT NULL,   -- from the order JSON instrumentName
        MessageContent   NVARCHAR(MAX)  NOT NULL,   -- order JSON as received from the Core LIS
        Status           NVARCHAR(20)   NOT NULL,
        RetryCount       INT            NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_RetryCount DEFAULT (0),
        MaxRetryAttempts INT            NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_MaxRetry DEFAULT (3),
        CreatedAt        DATETIME2(3)   NOT NULL,
        UpdatedAt        DATETIME2(3)   NOT NULL,
        LastRetryAt      DATETIME2(3)   NULL,
        NextRetryAt      DATETIME2(3)   NULL,
        ErrorMessage     NVARCHAR(MAX)  NULL,
        CONSTRAINT UQ_AnalyzerOutboundOrders_MessageId UNIQUE (MessageId),
        CONSTRAINT CK_AnalyzerOutboundOrders_Status
            CHECK (Status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'))
    );
    -- order retry job (PENDING and due), pending orders per analyzer, cleanup of old SUCCESS rows
    CREATE INDEX IX_AnalyzerOutboundOrders_Status_NextRetryAt
        ON dbo.AnalyzerOutboundOrders (Status, NextRetryAt) INCLUDE (CreatedAt);
    CREATE INDEX IX_AnalyzerOutboundOrders_Instrument_Status
        ON dbo.AnalyzerOutboundOrders (InstrumentName, Status, CreatedAt);
    CREATE INDEX IX_AnalyzerOutboundOrders_Status_UpdatedAt
        ON dbo.AnalyzerOutboundOrders (Status, UpdatedAt);
END
GO

/*
  OPTIONAL 1 - service login with rights on this database only (run as a sysadmin).
  Choose the password at run time; never commit it. The service reads it from DB_PASSWORD
  and the login name from DB_USERNAME.

  USE [master];
  CREATE LOGIN [astm-server] WITH PASSWORD = N'<choose-a-strong-password>', CHECK_POLICY = ON;
  USE [infinity-lis-astm-dev];
  CREATE USER [astm-server] FOR LOGIN [astm-server];
  ALTER ROLE db_datareader ADD MEMBER [astm-server];
  ALTER ROLE db_datawriter ADD MEMBER [astm-server];
  -- no db_ddladmin: the service does not create tables; no access to the Core LIS databases.

  OPTIONAL 2 - copy history from the old tables in a Core LIS database (same SQL Server instance).
  Stop the service first. Replace infinity-lis-shard-1 with the database that held the old tables.

  SET IDENTITY_INSERT dbo.AnalyzerInboundMessages ON;
  INSERT INTO dbo.AnalyzerInboundMessages (Id, MessageId, InstrumentName, RawMessage, MessageType, Status,
         ReceivedAt, ProcessedAt, PublishedAt, ErrorMessage, RemoteAddress)
  SELECT Id, MessageId, InstrumentName, RawMessage, MessageType, Status,
         ReceivedAt, ProcessedAt, PublishedAt, ErrorMessage, RemoteAddress
  FROM [infinity-lis-shard-1].dbo.ASTMServerMessages;
  SET IDENTITY_INSERT dbo.AnalyzerInboundMessages OFF;

  SET IDENTITY_INSERT dbo.AnalyzerOutboundOrders ON;
  INSERT INTO dbo.AnalyzerOutboundOrders (Id, MessageId, InstrumentName, MessageContent, Status, RetryCount,
         MaxRetryAttempts, CreatedAt, UpdatedAt, LastRetryAt, NextRetryAt, ErrorMessage)
  SELECT Id, MessageId, InstrumentName, MessageContent, Status, RetryCount,
         MaxRetryAttempts, CreatedAt, UpdatedAt, LastRetryAt, NextRetryAt, ErrorMessage
  FROM [infinity-lis-shard-1].dbo.ASTMOrderMessages;
  SET IDENTITY_INSERT dbo.AnalyzerOutboundOrders OFF;

  After checking the copy, the old tables can be dropped from the Core LIS database by its owner.
*/
