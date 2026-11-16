# Sysmex XN Simulator Test Runbook

Step-by-step test of the Sysmex XN path using the instrument simulator instead of the analyzer.
Run it on the stage (or a dev) machine that can reach SQL Server and RabbitMQ. Every step has an
expected result; do not move to the real analyzer until every box in section 9 is ticked.

Commands are PowerShell, run from the repository root.

---

## 0. Prerequisites

- JDK 17, 21 or 25 (the build is verified on 21 and 25; output runs on Java 8+) and Maven 3.6+.
- Network access from this machine to SQL Server and RabbitMQ (the `local` profile defaults are
  `192.168.20.123:1433` and `192.168.20.95:5672`; override with the variables in step 2).
- RabbitMQ management UI, normally `http://<rabbitmq-host>:15672`, with a login that can publish.
- SQL Server tables `ASTMServerMessages` and `ASTMOrderMessages` already exist (they do wherever
  ORTHO VISION has run).
- Ports 5000 (Sysmex XN) and 9001 (ORTHO VISION) free on this machine.

## 1. Build

```powershell
mvn -B clean package
```

Expected: `BUILD SUCCESS`. The tests run as part of the build. Lines logged at ERROR level during the
tests come from the failure-path tests and are expected.

Artifacts:
- `astm-server\target\astm-server-1.0.0.jar`
- `instrument-simulator\target\instrument-simulator-1.0.0.jar`

## 2. Configure secrets for this window

```powershell
$env:DB_PASSWORD = "<database password>"
# only if they differ from the profile defaults:
# $env:DB_HOST = "192.168.20.123"; $env:DB_NAME = "infinity-lis"; $env:DB_USERNAME = "medsa"
# $env:RABBITMQ_HOST = "192.168.20.95"; $env:RABBITMQ_USERNAME = "guest"; $env:RABBITMQ_PASSWORD = "guest"
```

Use the `staging` profile instead of `local` on the stage server (`--spring.profiles.active=staging`).

## 3. Start the server (terminal 1)

```powershell
java -jar astm-server\target\astm-server-1.0.0.jar --spring.profiles.active=local
```

Expected log lines:

```
[SysmexXN] Declared exchange sysmex-xn-exchange
[SysmexXN] Declared queue sysmex-xn.results.queue
[SysmexXN] Declared binding sysmex-xn-exchange -[result.inbound]-> sysmex-xn.results.queue
[SysmexXN] Declared queue sysmex-xn.orders.queue
Started order listener for instrument 'SysmexXN' on queue 'sysmex-xn.orders.queue'
Listener started for 'OrthoVision' on port 9001 using driver ...OrthoVisionDriver
Listener started for 'SysmexXN' on port 5000 using driver ...SysmexXnDriver
```

For the existing ORTHO VISION objects a `Could not declare ... using it as is` warning is fine: it
means the object already exists with slightly different settings and is reused.

Check in the RabbitMQ UI: **Exchanges** shows `sysmex-xn-exchange`; **Queues** shows
`sysmex-xn.results.queue` and `sysmex-xn.orders.queue`; the results queue's **Bindings** shows
`sysmex-xn-exchange` with routing key `result.inbound`.

> Nothing consumes `sysmex-xn.results.queue` yet, so test messages stay there for inspection.
> Purge it at the end (section 8) before the LIS starts consuming it.

## 4. Start the simulator (terminal 2)

```powershell
java -jar instrument-simulator\target\instrument-simulator-1.0.0.jar localhost 5000 sysmex-xn-test-cases.json
```

Expected: the simulator prints a menu of 9 test cases, then "Listen for Orders from Server" (10) and
"Exit" (11). The server logs `Accepted connection from /127.0.0.1:<port> for 'SysmexXN'`.

## 5. Results from the analyzer

How to inspect a published message: RabbitMQ UI, **Queues > sysmex-xn.results.queue > Get messages**,
Ack mode **Nack message requeue true**, Messages **1**, then **Get Message(s)**.

How to inspect the database:

```sql
SELECT TOP 10 Id, InstrumentName, MessageType, Status, ReceivedAt, ErrorMessage
FROM ASTMServerMessages WHERE InstrumentName = 'SysmexXN' ORDER BY Id DESC;
```

### 5.1 Full analysis result (menu 4)

Simulator: an ACK for every frame, then `Message sent successfully.`

Server log: `Parsed RESULT message from SysmexXN: 1 order(s), 31 result(s), 0 query(s), 3 comment(s)`
and `Successfully published result from instrument 'SysmexXN'`.

Database: newest row `MessageType = RESULT`, `Status = PUBLISHED`.

Queue message, check these values:

| Path | Expected |
|---|---|
| `messageType` | `RESULT` |
| `instrumentName` | `SysmexXN` |
| `patientRecord.practiceAssignedPatientId` | `100` |
| `patientRecord.patientName` | `Brown^Jim` |
| `patientRecord.birthDate` | `20010820000000` |
| `patientRecord.location` | `WEST` |
| `orderRecords[0].specimenId` | `1234567890` (no leading spaces) |
| `orderRecords[0].rackNumber` / `rackPosition` / `sampleNumberAttribute` | `2` / `1` / `B` |
| first result | `parameterName WBC`, `dataValue 7.81`, `units 10*3/uL`, `resultCategory CLINICAL` |
| `RBC` result | `dataValue ----`, `resultAbnormalFlags A` |
| `Blasts/Abn_Lympho?` | `dataValue 100`, `resultCategory IP_SUSPECT` |
| `PLT_Abn_Distribution` | `resultCategory IP_ABNORMAL` |
| `ACTION_MESSAGE_Delta` | `resultCategory ACTION_MESSAGE` |
| `SCAT_DIFF` | `resultCategory IMAGE`, path with `\` separators |
| `commentRecords` | 3 entries: scope `PATIENT`, `ORDER`, `RESULT`; the last has `ruleReferences` 1 `WBC HIGH` and 23 `Need to PLT-F analysis` |

### 5.2 Other result types

| Menu | Case | Expected |
|---|---|---|
| 5 | Body fluid | `RESULT`; parameters `WBC-BF`, `RBC-BF`, `MN#`, `MN%`, `PMN#`, `PMN%`; empty `rackNumber` |
| 6 | Real-time QC | `messageType QC_RESULT`; `specimenId QC-12345678`; `actionCode Q`; values `resultCategory QC` |
| 7 | Manual QC | `QC_RESULT`; `specimenId 1` (QC file number) |
| 8 | Slide result | `parameterName SMEAR`, `dataValue OK`, `resultCategory SLIDE` |
| 9 | Accented name | no NAK in the simulator log; `patientName` `Müller^José` (accents intact) |

## 6. Inquiry and order response (the round trip)

This is the most important test: it is what the analyzer does for every barcoded tube.

1. In the simulator choose **3** (sampler-mode inquiry).
   Server log: `Parsed QUERY message from SysmexXN`. Queue message:
   `queryRecords[0]`: `rackNumber 2`, `rackPosition 1`, `sampleId 1234567890`,
   `sampleNumberAttribute B`, `requestInformationStatusCodes N`.
2. In the simulator choose **10** (Listen for Orders from Server). Do this **before** step 3: a real
   analyzer is always listening, the simulator is not.
3. In the RabbitMQ UI: **Queues > sysmex-xn.orders.queue > Publish message**. Leave Properties
   empty (or `content_type = application/json`) and paste the contents of
   `instrument-simulator\src\main\resources\test-messages\sysmex-xn-order-response.json`. Publish.
4. Expected simulator output within a second:

   ```
   <- ENQ (Server wants to send an order)
   --- ORDER RECEIVED ---
   H|\^&|||||||||||E1394-97
   P|1|||100|^Jim^Brown||20010820|M|||||^Dr.1|||||||||||^^^WEST
   C|1||Patient Comments
   O|1|2^1^            1234567890^B||^^^^WBC\^^^^RBC\ ... \^^^^IRF||20010807101000|||||N||||||||||||||Q
   C|1||Sample Comments
   L|1|N
   ```

   This must match the specification example in section 5.2.1 (the sample ID is right-aligned with
   spaces to 22 characters; that is required by the analyzer).
5. Database:

   ```sql
   SELECT TOP 10 Id, InstrumentName, Status, RetryCount, NextRetryAt, ErrorMessage
   FROM ASTMOrderMessages WHERE InstrumentName = 'SysmexXN' ORDER BY Id DESC;
   ```

   Newest row `Status = SUCCESS`.
6. Press Enter in the simulator to stop listening.

### 6.1 Variations

Repeat steps 2 to 4 with edited copies of the JSON:

| Change in the JSON | Expected `O` record ending |
|---|---|
| remove `universalTestId`, set `"reportTypes": "Y"` (no order) | `...|||||N||||||||||||||Y` and no `^^^^` parameter list |
| `"reportTypes": "X"` (do not aspirate) | `...||||||||||||||X` |
| `"specimenId": "QC-1"` | action code `Q` instead of `N` |
| `"universalTestId": "WBC,RBC,PLT"` | `^^^^WBC\^^^^RBC\^^^^PLT` |

## 7. Failure and recovery

| Test | How | Expected |
|---|---|---|
| Analyzer offline | Exit the simulator (menu 11). Publish the order JSON. | Log: `Instrument 'SysmexXN' is not connected`. Row stays `PENDING`; the retry job (every minute) bumps `RetryCount` and sets `NextRetryAt` about 5 minutes ahead. |
| Recovery | Start the simulator again, choose 10, wait for `NextRetryAt`. | Order delivered automatically, row becomes `SUCCESS`. After `max-attempts` (3 in `local`) without a connection the row becomes `FAILED`. |
| Reconnect replaces stale session | With one simulator connected, start a second simulator on port 5000. | Log: `Instrument 'SysmexXN' already had a session ... Replacing it`. The second simulator works; the first one's next send fails. |
| RabbitMQ down (optional) | Stop RabbitMQ briefly, send menu 4, start RabbitMQ. | Row `PUBLISH_RETRY`, then `PUBLISHED` within about 2 minutes. |

## 8. ORTHO VISION regression and cleanup

1. Start a simulator for ORTHO VISION in a third terminal:
   `java -jar instrument-simulator\target\instrument-simulator-1.0.0.jar localhost 9001`
   Send the keep-alive, a query and one result case. Expected: same behaviour as before this
   branch (rows `PUBLISHED` with `InstrumentName = 'OrthoVision'`).
2. RabbitMQ UI: **Queues > sysmex-xn.results.queue > Purge Messages** so test data never reaches
   the LIS.
3. Optional: delete test rows (`WHERE InstrumentName = 'SysmexXN' AND ReceivedAt >= '<test start>'`).

## 9. Exit criteria before connecting the real analyzer

- [ ] Build passes on the stage machine (section 1).
- [ ] Queues, exchange and binding exist (section 3).
- [ ] Full result published with every value in the 5.1 table correct.
- [ ] QC typed `QC_RESULT`; accented name intact.
- [ ] Order response matches the spec example byte for byte; row `SUCCESS`.
- [ ] Offline order recovers after reconnect; second connection replaces the first.
- [ ] ORTHO VISION regression unchanged.
- [ ] Results queue purged.

## 10. Then: first run with the analyzer

1. Open the firewall on the ASTM server for the analyzer:
   `New-NetFirewallRule -DisplayName "ASTM SysmexXN" -Direction Inbound -Protocol TCP -LocalPort 5000 -Action Allow`
2. On the IPU, Host Computer 1: Host IP = this server, Port = 5000, Format unchanged
   (`XN series ASTM1381-02/ASTM1394-97`). Leave Host Computer 2 as it is.
3. Server log must show `Accepted connection from <IPU address> for 'SysmexXN'`.
4. If the LIS does not yet answer inquiries automatically, run the first samples with the IPU's
   query-to-host turned off (or expect the IPU to wait for its timeout and then use its default
   order). A person publishing orders by hand is too slow for a real inquiry.
5. Run one QC and one patient sample; check them as in section 5. Check which P field the IPU shows
   the ward from (see the note in `sysmex-xn-integration.md`).
6. To roll back, restore the original Host Computer 1 values from the screenshots.
