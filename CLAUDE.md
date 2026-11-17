# ASTM Interface Service

Bidirectional ASTM E1381-02 / E1394-97 interface between laboratory analyzers and the Infinity LIS.
Java 8 source level, Spring Boot 2.7, Maven multi-module. Deployed on Windows as a service.

## Architecture page: keep it current (mandatory)

`docs/architecture.html` is the single overview of this repository (modules, startup, ports, every
flow with diagrams, record and message types per analyzer, RabbitMQ topology, tables and statuses,
configuration, deployment, open items). Read it first to understand the system.

Any change to a flow, record or message type, queue, exchange, routing key, table or column, status,
port, driver, retry rule, startup or deployment step must update `docs/architecture.html` in the same
commit: text, tables and the affected SVG diagram, plus the "Updated" date in its header.
`ArchitectureDocTest` (astm-server tests) fails the build when configured instruments, ports, drivers,
queues, exchanges, routing keys or statuses are missing from the page; fix the page, never the test.
The deeper single-topic documents in `docs/` must agree with the page.

## Modules

- `astm-model` - shared record model (`AstmMessage`, H/P/O/R/Q/C/M/L records), Jackson JSON, no Spring.
- `astm-server` - the deployable. TCP listener per instrument, protocol state machine, instrument
  drivers, SQL Server persistence (`AnalyzerInboundMessages`, `AnalyzerOutboundOrders`), RabbitMQ in/out.

## Database (own ASTM database, not shared with Core LIS)

SQL Server database `infinity-lis-astm-<env>` (`-dev` for local and dev, `-stage`, `-prod`; override
with `DB_NAME`). Only this service uses it. Tables: `AnalyzerInboundMessages` (everything analyzers
send) and `AnalyzerOutboundOrders` (orders waiting for an analyzer); formerly `ASTMServerMessages` /
`ASTMOrderMessages` in the Core LIS database. The app never creates tables: the schema lives in
`astm-server/src/main/resources/db/astm-schema.sql`. A column or table change updates that script and
`docs/architecture.html` in the same commit; `SchemaScriptTest` fails the build when the script and the
repositories disagree.
- `instrument-simulator` - CLI that plays an analyzer against the server from JSON test cases.
- `astm-parser-swing` - desktop GUI to inspect raw ASTM messages.

## Build and test

```bash
mvn -B verify                 # full build with tests (what CI runs)
mvn -q -pl astm-server test   # server tests only
```

Tests need no database or broker: protocol tests use a real socket pair with a fake instrument,
handler tests mock the persistence layer. Running the server itself needs SQL Server and RabbitMQ
plus `DB_PASSWORD` (see README). Do not commit passwords; profiles read them from the environment.

## Key code paths

- `server/core/ASTMServer` - one listener thread per instrument, one active session per instrument;
  a new connection replaces a stale one.
- `server/core/InstrumentConnectionHandler` - per-session event loop: send one queued order, then
  poll the socket (500 ms read timeout). Reports order outcomes through `OrderOutcomeListener`.
- `server/protocol/ASTMProtocolStateMachine` + `ChecksumUtils` - E1381 framing. Wire charset is
  ISO-8859-1. Sender timer 15 s, receiver timer 30 s, 20 s back-off after ENQ contention.
- `server/driver/InstrumentDriver` - `parse(raw)` and `build(AstmMessage)`; one implementation per
  analyzer, selected by `lis.instruments[].driverClassName`:
  - `OrthoVisionDriver` (blood bank, M well records)
  - `SysmexXnDriver` (hematology; spec examples are the tests, see `docs/sysmex-xn-integration.md`)
- `server/messaging/OrderQueueListener` -> `OrderMessageService` -> handler queue (orders in);
  `ResultQueuePublisher` (results out). Messaging is gated by `lis.messaging.enabled`.

## RabbitMQ (standard Infinity LIS topology)

The broker objects are provisioned by the platform script `infinity-lis-rabbitmq-topology.ps1`
(not in this repo), never by this service: `lis.messaging.declare-topology=false` in every profile,
and the `astm-server` broker user has no configure permission (publish `lis.inbound.x` only, consume
`lis.orders.instrument.astm.outbound.q` only). Vhost `infinity-lis-<env>` (no leading slash).

| Flow | Exchange / routing key | Queue |
|---|---|---|
| Patient results `RESULT` | `lis.inbound.x` / `results.instrument.astm` | `lis.results.instrument.inbound.q` (shared with HL7/Mirth: never purge) |
| QC `QC_RESULT` | `lis.inbound.x` / `qc.instrument.astm` | `lis.qc.instrument.inbound.q` (QC must never reach patient filing) |
| Queries `QUERY` | `lis.inbound.x` / `results.instrument.astm.query` | results queue until a query lane exists |
| Orders in | `lis.outbound.x` / `orders.instrument.astm.worklist` | `lis.orders.instrument.astm.outbound.q` (all ASTM analyzers share it; routed by JSON `instrumentName`) |

Routing per type: `InstrumentConfig.routingKeyFor` (ignores a `RETRY_` prefix). Results are marked
PUBLISHED only after a broker confirm and no return. Invalid or unknown-instrument orders are rejected
to the `.dlq`. Every queue is quorum with a delivery limit and DLQ.

## Conventions

- Records are CR separated; the receiver normalises each record to `CR LF`. Drivers split on `\r`.
- Field indexes in drivers are 0-based array positions of the ASTM 1-based field numbers minus one
  (ASTM field 7.1.13 -> `fields[12]`).
- Instrument-specific quirks live in the driver, never in the state machine or handler.
- New analyzer: add a driver + tests built from the vendor spec's example messages, a profile entry
  in every `application-*.yml`, and a simulator test-case file.
- Keep the shared model backward compatible: add optional fields, do not rename JSON properties
  (the LIS consumes them).

## Documents

- `docs/architecture.html` - start here: the whole system on one page (see the rule above).
- `docs/sysmex-xn-readiness-review.md` - review of the code against the Sysmex XN spec and status.
- `docs/sysmex-xn-integration.md` - IPU settings and the LIS JSON contract for the XN.
- `docs/staging-test-checklist.md` - what to verify on stage for each analyzer.
- `docs/sysmex-xn-simulator-test.md` - step-by-step simulator runbook for the XN before the real analyzer.
- `astm_message_specs.md` - ORTHO VISION record specification notes.
