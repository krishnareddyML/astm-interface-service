# ASTM Interface Service

Bidirectional ASTM E1381-02 / E1394-97 interface between laboratory analyzers and the Infinity LIS.
Java 8 source level, Spring Boot 2.7, Maven multi-module. Deployed on Windows as a service.

## Modules

- `astm-model` - shared record model (`AstmMessage`, H/P/O/R/Q/C/M/L records), Jackson JSON, no Spring.
- `astm-server` - the deployable. TCP listener per instrument, protocol state machine, instrument
  drivers, SQL Server persistence (`ASTMServerMessages`, `ASTMOrderMessages`), RabbitMQ in/out.
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

- `docs/sysmex-xn-readiness-review.md` - review of the code against the Sysmex XN spec and status.
- `docs/sysmex-xn-integration.md` - IPU settings and the LIS JSON contract for the XN.
- `docs/staging-test-checklist.md` - what to verify on stage for each analyzer.
- `astm_message_specs.md` - ORTHO VISION record specification notes.
