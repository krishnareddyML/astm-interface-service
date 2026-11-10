# Staging Test Checklist

What has been verified by automated tests, what has not, and what to check on the stage systems
before the branch is promoted.

## Verified in this branch (automated, no analyzer needed)

- E1381 framing, checksums, frame numbering, ETB/ETX reassembly, duplicate and bad frames, ENQ
  contention, NAK retransmission (socket-level tests with a fake instrument).
- Connection handler: results published, keep-alive ignored, orders sent and reported, failures
  reported, queue handed back on disconnect.
- ORTHO VISION driver: parse of a captured ABO/Rh result and query, order building.
- Sysmex XN driver: every example message in the XN spec, exact order-response output.
- Whole build on JDK 21 with Java 8 target; CI workflow added.

## Not verified here (needs the stage environment)

- Application start-up against SQL Server and RabbitMQ with the new env-var based configuration.
- Any traffic with a real ORTHO VISION or Sysmex XN analyzer.
- LIS-side consumption of the new JSON fields and the XN order-response contract.

## Behaviour changes that affect the ORTHO VISION link

Watch these specifically; they are improvements but they change bytes on the wire or DB state.

| Change | What to confirm on stage |
|---|---|
| Built records no longer end with an extra `\|` | Analyzer accepts orders (it did with the extra delimiter; standard output should be fine). |
| ETB continuation frames no longer carry a CR | Only matters if the LIS sends records longer than one frame; the server does not split by default. |
| Orders now end as SUCCESS / PENDING(retry) / FAILED | `ASTMOrderMessages.Status` moves off PROCESSING after a send. |
| A reconnecting analyzer replaces the old session | Power-cycle the analyzer (or pull the cable) and confirm it reconnects without restarting the service. |
| Outbound orders go out within ~0.5 s of queueing | Query to order round-trip time in the logs. |
| Extended characters (accents) accepted | Send a patient with an accented name; no NAK loop. |
| `ResultQueueTestListener` is off | The LIS consumer receives every result (nothing competes). |

## Stage procedure

1. Deploy with `SPRING_PROFILES_ACTIVE=staging` and `DB_PASSWORD`, `RABBITMQ_PASSWORD` set.
   Confirm in the log: `Listener started for 'OrthoVision' on port 9001` and
   `Listener started for 'SysmexXN' on port 5000`.
2. ORTHO VISION regression: keep-alive, query -> order response, result with M records, two-sample
   result, analyzer restart mid-session.
3. Sysmex XN bench test (IPU host setting ASTM 1381-02/1394-97, TCP, port 5000):
   - manual-mode query and sampler-mode query; LIS answers with report type `Q`;
   - "no order" answer (`Y`) and "do not aspirate" (`X`);
   - CBC+DIFF result, result with IP flags and action message, QC result;
   - confirm which P field the IPU shows the ward from (spec table says 26, examples say 25);
   - unit and decimal settings match what the LIS expects.
4. Simulator runs for anything the analyzers cannot easily produce (Latin-1 names, long comments):
   `java -jar instrument-simulator.jar <host> 5000 sysmex-xn-test-cases.json`.
5. Rotate the database and RabbitMQ passwords that were committed before this branch.

## Still open after stage

- Spring Boot 2.7 / Java 8 upgrade (Boot 3.x, Java 17) once both analyzers are stable.
- End-to-end integration test with an embedded database and broker so start-up is covered by CI.
