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

  Column types and defaults are those of the original tables ASTMServerMessages and
  ASTMOrderMessages (scripted from the Core LIS database), under the new names:
    ASTMServerMessages -> AnalyzerInboundMessages
    ASTMOrderMessages  -> AnalyzerOutboundOrders

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
        Id                BIGINT IDENTITY(1,1) NOT NULL,
        MessageId         VARCHAR(255)  NOT NULL,   -- UUID assigned on receipt
        InstrumentName    VARCHAR(100)  NOT NULL,   -- lis.instruments[].name
        RawMessage        VARCHAR(MAX)  NOT NULL,   -- records as received, CR LF separated
        ParsedJsonMessage VARCHAR(MAX)  NULL,       -- kept from the original table; not written by the code today
        MessageType       VARCHAR(50)   NULL,       -- RESULT, QC_RESULT, QUERY, ...
        Status            VARCHAR(20)   NOT NULL CONSTRAINT DF_AnalyzerInboundMessages_Status DEFAULT ('RECEIVED'),
        ReceivedAt        DATETIME      NULL     CONSTRAINT DF_AnalyzerInboundMessages_ReceivedAt DEFAULT (GETDATE()),
        ProcessedAt       DATETIME      NULL,
        PublishedAt       DATETIME      NULL,
        ErrorMessage      VARCHAR(MAX)  NULL,
        RemoteAddress     VARCHAR(100)  NULL,       -- analyzer IP:port
        CONSTRAINT PK_AnalyzerInboundMessages PRIMARY KEY CLUSTERED (Id),
        CONSTRAINT UQ_AnalyzerInboundMessages_MessageId UNIQUE NONCLUSTERED (MessageId),
        CONSTRAINT CK_AnalyzerInboundMessages_Status
            CHECK (Status IN ('RECEIVED', 'PROCESSED', 'PUBLISHED', 'PUBLISH_RETRY', 'ERROR'))
    );
    -- not in the original tables; they serve the retry job (findByStatus), the cleanup of old
    -- PUBLISHED rows and "recent messages per analyzer"
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
        Id               BIGINT IDENTITY(1,1) NOT NULL,
        MessageId        VARCHAR(255)  NOT NULL,   -- UUID assigned when consumed from RabbitMQ
        InstrumentName   VARCHAR(100)  NOT NULL,   -- from the order JSON instrumentName
        MessageContent   VARCHAR(MAX)  NOT NULL,   -- order JSON as received from the Core LIS
        Status           VARCHAR(20)   NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_Status DEFAULT ('PENDING'),
        RetryCount       INT           NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_RetryCount DEFAULT (0),
        MaxRetryAttempts INT           NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_MaxRetry DEFAULT (5),
        CreatedAt        DATETIME      NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_CreatedAt DEFAULT (GETDATE()),
        UpdatedAt        DATETIME      NOT NULL CONSTRAINT DF_AnalyzerOutboundOrders_UpdatedAt DEFAULT (GETDATE()),
        LastRetryAt      DATETIME      NULL,
        NextRetryAt      DATETIME      NULL,
        ErrorMessage     VARCHAR(MAX)  NULL,
        CONSTRAINT PK_AnalyzerOutboundOrders PRIMARY KEY CLUSTERED (Id),
        CONSTRAINT UQ_AnalyzerOutboundOrders_MessageId UNIQUE NONCLUSTERED (MessageId),
        CONSTRAINT CK_AnalyzerOutboundOrders_Status
            CHECK (Status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'))
    );
    -- not in the original tables; they serve the order retry job (PENDING and due), pending
    -- orders per analyzer and the cleanup of old SUCCESS rows
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
  INSERT INTO dbo.AnalyzerInboundMessages (Id, MessageId, InstrumentName, RawMessage, ParsedJsonMessage,
         MessageType, Status, ReceivedAt, ProcessedAt, PublishedAt, ErrorMessage, RemoteAddress)
  SELECT Id, MessageId, InstrumentName, RawMessage, ParsedJsonMessage,
         MessageType, Status, ReceivedAt, ProcessedAt, PublishedAt, ErrorMessage, RemoteAddress
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
