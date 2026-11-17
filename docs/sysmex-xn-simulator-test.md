# Sysmex XN Simulator Test Runbook

Step-by-step test of the Sysmex XN path using the instrument simulator instead of the analyzer.
Run it on the stage (or a dev) machine that can reach SQL Server and RabbitMQ. Every step has an
expected result; do not move to the real analyzer until every box in section 9 is ticked.

Commands are PowerShell, run from the repository root.

---

## 0. Prerequisites

- JDK 17, 21 or 25 (the build is verified on 21 and 25; output runs on Java 8+) and Maven 3.6+.
- Network access from this machine to SQL Server and RabbitMQ (the `local` profile defaults are  `192.168.20.123:1433` and `192.168.20.254:5672`, vhost `infinity-lis-dev`; override with the variables in step 2).
- RabbitMQ management UI, normally `http://<rabbitmq-host>:15672`, with a login that can publish.
- The ASTM database `infinity-lis-astm-dev` exists on SQL Server with its two tables
  (`AnalyzerInboundMessages`, `AnalyzerOutboundOrders`): run `astm-server/src/main/resources/db/astm-schema.sql` in it once.
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
$env:RABBITMQ_PASSWORD = "<password of the astm-server RabbitMQ user>"
# only if they differ from the profile defaults:
# $env:DB_HOST = "192.168.20.123"; $env:DB_NAME = "infinity-lis-astm-dev"; $env:DB_USERNAME = "medsa"
# $env:RABBITMQ_HOST = "192.168.20.254"; $env:RABBITMQ_VHOST = "infinity-lis-dev"; $env:RABBITMQ_USERNAME = "astm-server"
```

The service connects as the `astm-server` user created by `infinity-lis-rabbitmq-topology.ps1`.
That user may only publish to `lis.inbound.x` and consume `lis.orders.instrument.astm.outbound.q`;
it cannot create anything, so the broker objects must already exist (they do on 192.168.20.254).

Use the `staging` profile instead of `local` on the stage server (`--spring.profiles.active=staging`).

## 3. Start the server (terminal 1)

```powershell
java -jar astm-server\target\astm-server-1.0.0.jar --spring.profiles.active=local
```

Expected log lines:

```
Setting up instrument queues (declare topology: false)...
Started order listener on queue 'lis.orders.instrument.astm.outbound.q'
Configured messaging for instrument 'OrthoVision': orders from lis.orders.instrument.astm.outbound.q; publish to exchange lis.inbound.x with keys results=results.instrument.astm, qc=qc.instrument.astm, query=results.instrument.astm.query
Configured messaging for instrument 'SysmexXN': ... (same)
Listener started for 'OrthoVision' on port 9001 using driver ...OrthoVisionDriver
Listener started for 'SysmexXN' on port 5000 using driver ...SysmexXnDriver
```

An `ACCESS_REFUSED` or `NOT_FOUND` for the order queue means a wrong vhost, password or missing
topology. Check in the RabbitMQ UI (vhost `infinity-lis-dev`): **Queues >
lis.orders.instrument.astm.outbound.q** shows 1 consumer.

Where things go (standard topology):

| From the analyzer | Exchange / routing key | Queue |
|---|---|---|
| Patient results (`RESULT`) | `lis.inbound.x` / `results.instrument.astm` | `lis.results.instrument.inbound.q` |
| QC results (`QC_RESULT`) | `lis.inbound.x` / `qc.instrument.astm` | `lis.qc.instrument.inbound.q` |
| Host queries (`QUERY`) | `lis.inbound.x` / `results.instrument.astm.query` | `lis.results.instrument.inbound.q` (no query lane yet) |
| Orders to the analyzer | `lis.outbound.x` / `orders.instrument.astm.worklist` | `lis.orders.instrument.astm.outbound.q` |

> Nothing consumes the results and QC queues on the dev broker yet, so test messages stay there
> for inspection. `lis.results.instrument.inbound.q` is shared with HL7 instruments (Mirth): never
> purge it; remove only your own test messages (section 8).

## 4. Start the simulator (terminal 2)

```powershell
java -jar instrument-simulator\target\instrument-simulator-1.0.0.jar localhost 5000 sysmex-xn-test-cases.json
```

Expected: the simulator prints a menu of 9 test cases, then "Listen for Orders from Server" (10) and
"Exit" (11). The server logs `Accepted connection from /127.0.0.1:<port> for 'SysmexXN'`.

## 5. Results from the analyzer

How to inspect a published message: RabbitMQ UI, **Queues > lis.results.instrument.inbound.q**
(QC: `lis.qc.instrument.inbound.q`) **> Get messages**, Ack mode **Nack message requeue true**,
Messages **1**, then **Get Message(s)**. The headers `instrumentName` and `messageType` and the
routing key identify the message.

How to inspect the database:

```sql
SELECT TOP 10 Id, InstrumentName, MessageType, Status, ReceivedAt, ErrorMessage
FROM AnalyzerInboundMessages WHERE InstrumentName = 'SysmexXN' ORDER BY Id DESC;
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
| 6 | Real-time QC | in `lis.qc.instrument.inbound.q` (not the results queue); `messageType QC_RESULT`; `specimenId QC-12345678`; `actionCode Q`; values `resultCategory QC` |
| 7 | Manual QC | in `lis.qc.instrument.inbound.q`; `QC_RESULT`; `specimenId 1` (QC file number) |
| 8 | Slide result | `parameterName SMEAR`, `dataValue OK`, `resultCategory SLIDE` |
| 9 | Accented name | no NAK in the simulator log; `patientName` `Müller^José` (accents intact) |

## 6. Inquiry and order response (the round trip)

This is the most important test: it is what the analyzer does for every barcoded tube.

1. In the simulator choose **3** (sampler-mode inquiry).
   Server log: `Parsed QUERY message from SysmexXN`. Queue message (results queue, routing key
   `results.instrument.astm.query`):
   `queryRecords[0]`: `rackNumber 2`, `rackPosition 1`, `sampleId 1234567890`,
   `sampleNumberAttribute B`, `requestInformationStatusCodes N`.
2. In the simulator choose **10** (Listen for Orders from Server). Do this **before** step 3: a real
   analyzer is always listening, the simulator is not.
3. In the RabbitMQ UI (logged in as admin): **Exchanges > lis.outbound.x > Publish message**,
   Routing key `orders.instrument.astm.worklist` (this is how the core LIS will send it). Leave
   Properties empty (or `content_type = application/json`) and paste the contents of
   `instrument-simulator\src\main\resources\test-messages\sysmex-xn-order-response.json`. Publish;
   the UI must say "Message published" (not "published, but not routed").
   `instrumentName` in the JSON must be exactly `SysmexXN`: an order for an unknown or misspelt
   instrument is rejected and lands in `lis.orders.instrument.astm.outbound.dlq`.
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
   FROM AnalyzerOutboundOrders WHERE InstrumentName = 'SysmexXN' ORDER BY Id DESC;
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
| Bad order JSON | Publish `{"foo":1}` with the order routing key. | Log shows the rejection; the message is in `lis.orders.instrument.astm.outbound.dlq`; no database row. |
| RabbitMQ down (optional) | Stop RabbitMQ briefly, send menu 4, start RabbitMQ. | Row `PUBLISH_RETRY`, then `PUBLISHED` within about 2 minutes. A row is marked `PUBLISHED` only after the broker confirms it. |

## 8. ORTHO VISION regression and cleanup

1. Start a simulator for ORTHO VISION in a third terminal:
   `java -jar instrument-simulator\target\instrument-simulator-1.0.0.jar localhost 9001`
   Send the keep-alive, a query and one result case. Expected: same behaviour as before this
   branch (rows `PUBLISHED` with `InstrumentName = 'OrthoVision'`).
2. Remove the test messages so they never reach the LIS. **Do not purge**
   `lis.results.instrument.inbound.q`: HL7 instrument results share it. If, as on the dev broker,
   it holds only your test messages, use **Get messages** with Ack mode **Reject requeue false**
   for that count (dead-letters them to the `.dlq`, which expires them). `lis.qc.instrument.inbound.q`
   likewise.
3. Optional: delete test rows (`WHERE InstrumentName = 'SysmexXN' AND ReceivedAt >= '<test start>'`).

## 9. Exit criteria before connecting the real analyzer

- [ ] Build passes on the stage machine (section 1).
- [ ] Service connects as `astm-server` and consumes the ASTM order queue (section 3).
- [ ] Patient results in the results queue, QC only in the QC queue.
- [ ] Full result published with every value in the 5.1 table correct.
- [ ] QC typed `QC_RESULT`; accented name intact.
- [ ] Order response matches the spec example byte for byte; row `SUCCESS`.
- [ ] Offline order recovers after reconnect; second connection replaces the first.
- [ ] ORTHO VISION regression unchanged.
- [ ] Test messages removed from the results and QC queues.

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
