# Sysmex XN Readiness Review

Review of `astm-interface-service` against *Automated Hematology Analyzer XN series ASTM Host
Interface Specifications (North America), Revision 11* (Sysmex document R314187).
The service was built and tested against the ORTHO VISION blood bank analyzer about a year ago.
This document answers: **can it talk to a Sysmex XN as it stands, and what has to change?**

Date: 2026-11-10. Build state: `mvn compile` passes on all four modules (JDK 21, target 1.8).

---

## 1. Verdict

**The transport and framework are reusable. The message layer is not.** Code changes are
required before an XN can be connected, and a handful of core bugs found during the review
should be fixed at the same time because they affect the Ortho Vision link too.

| Layer | Status for XN | Summary |
|---|---|---|
| TCP role and session (ASTM E1381-02 mode) | Reusable | Host listens, IPU connects. ENQ/ACK/EOT, STX/ETX framing, checksum, frame numbers 1..7,0 all match the spec. |
| Framing details | Minor fixes | Extended (Latin-1) characters break checksum validation; ENQ contention not handled; receiver timer is 15 s, spec says 30 s. |
| Record parsing (IPU to host) | **Not usable** | Sample ID, units, birth date, patient ID, query key and comment records are lost or mis-mapped. Verified by running the current driver on the spec's own examples. |
| Record building (host to IPU) | **Not usable** | Header, patient and order records are emitted in Ortho/LIS2-A shape, not the XN shape. |
| Config, persistence, RabbitMQ | Reusable | Add a second instrument entry. One production profile bug. |
| Core robustness | Fix before go-live | Stale connection blocks reconnection; failed order sends are silently dropped; up to 30 s latency on query responses. |

Estimated effort: roughly 2 to 3 developer weeks for a working XN driver plus the core fixes,
plus bench testing with the analyzer.

---

## 2. What already matches the XN spec

- **Connection direction** (spec 4.2, 6.2): IPU is the TCP client, host is the server. `ASTMServer` listens per instrument on a configured port. The IPU default host port is 5000 and is configurable on the IPU.
- **Establishment and termination** (4.2.2, 4.2.4): host answers ENQ with ACK, and ends on EOT. `ASTMProtocolStateMachine.receiveMessage()` does exactly this.
- **Frame structure** (4.2.3): `<STX> F# text <ETB|ETX> C1 C2 <CR><LF>`, checksum is the 8-bit sum from the character after STX through ETB/ETX, hex uppercase. `ChecksumUtils` matches.
- **Frame numbering** (4.2.3): starts at 1, cycles 1..7 then 0. Both send and receive sides wrap correctly.
- **One record per frame, no record splitting on TCP** (4.2.3 note, 6.4): the receiver concatenates ETB frames and terminates on ETX, so it also tolerates split records.
- **Driver plug-in design**: `InstrumentDriver` is loaded by class name per instrument, so a Sysmex driver can live beside the Ortho driver without touching the server core.
- **Keep-alive detection** is harmless for XN (the IPU does not send keep-alives).

---

## 3. Gaps against the XN spec

### 3.1 Protocol / data link layer (`ASTMProtocolStateMachine.java`)

| # | Finding | Spec ref | Location | Severity |
|---|---|---|---|---|
| P1 | Frames are decoded as US-ASCII before checksum validation. Any byte 0x80 to 0xFE (spec allows "extended single-byte characters", e.g. accented names, half-width kana) becomes U+FFFD and the checksum fails, so the host NAKs, the IPU retries 6 times and aborts the whole message. Verified in a probe: a Latin-1 `é` in a P record fails validation. | 3 (Terminology), 4.2.3 (5) | `ASTMProtocolStateMachine.java:215-218`, `ChecksumUtils.validateFrameChecksum` | High |
| P2 | ENQ contention is not handled. If the host sends ENQ while the IPU sends ENQ, `waitForAck()` ignores the incoming ENQ and times out after 15 s. The spec requires the host to yield, ACK the IPU, and wait 20 s before re-sending its own ENQ. For XN this matters because the host sends order responses right after the IPU's query, when the IPU may already be starting its next transmission. | 4.2.2 (2) | `ASTMProtocolStateMachine.java:184` | Medium |
| P3 | Receiver inter-frame timeout is 15 s. The spec's receiver timer is 30 s. The sender side (15 s wait for ACK) is correct. | 4.2.5 (2) | `ASTMProtocolStateMachine.java:27` | Low |
| P4 | A retransmitted duplicate frame (IPU did not see our ACK) is NAKed as "out of sequence" instead of being ACKed and discarded. | E1381 retransmission rule | `ASTMProtocolStateMachine.java:145` | Low |
| P5 | The IPU must be set to **"ASTM 1381-02 / 1394-97"** host mode. In "1381-95" TCP mode the IPU sends bare records with no ENQ/ACK/EOT, and `receiveMessage()` will discard every byte that is not ENQ. This is an on-site configuration item, not code, but the service will look dead if it is wrong. | 4 note, 6.4 | n/a | Config |

### 3.2 Record parsing, IPU to host (`OrthoVisionDriver.java`)

The current driver was run against the spec's own example messages (sections 4.3.3 and 5.3.1).
Result:

```
P.practiceAssignedPatientId=   P.patientIdAlternate=100   birthDate=null   location=null
O.specimenId=''   O.instrumentSpecimenId=null   tests=^^^^WBC\^^^^RBC\^^^^HGB
R testId=^^^^WBC^1^^W  value=7.81  units=null  components=[, ^^^WBC^1^^W]
Q startingRangeId='2^1^      1234567890^B'   getSpecimenId()=''
C records: "Ignoring unsupported record type"
```

| # | Finding | Spec ref | Location | Severity |
|---|---|---|---|---|
| R1 | **Sample ID is lost on results.** XN sends the sample in O field 9.4.4 (Instrument Specimen ID) as `Rack^Position^SampleID^Attribute`; field 9.4.3 is empty. The driver only reads 9.4.3. Every result message would reach the LIS with no sample ID. | 4.3.3.4 | `OrthoVisionDriver.java:179` | **Critical** |
| R2 | **Sample ID is lost on queries.** Q field 12.1.3 is `Rack^Position^SampleID^Attribute` (sampler mode) or `^^SampleID^Attribute` (manual mode). `QueryRecord.getSpecimenId()` assumes the Ortho shape `^SID` and returns empty. The sample ID is 22 characters, right-aligned and space-padded, and must be echoed back **unchanged** in the order response. Rack and position must also be echoed back. | 4.3.3.3, 4.3.3.4 (2) | `QueryRecord.java:97`, `OrthoVisionDriver.java:208` | **Critical** |
| R3 | Units (R field 10.1.5, e.g. `10*3/uL`, `g/dL`, `%`) are not parsed. Ortho never sends units so the driver skips them. | 4.3.3.5 | `OrthoVisionDriver.java:194` | High |
| R4 | Parameter name is not extractable. XN test ID is `^^^^WBC^1^^W` (4 leading carets, then parameter, dilution ratio 1 or 5, unused, extended-order flag W). `ResultRecord.getTestIdComponents()` splits into two parts only. | 4.3.3.5 a) | `ResultRecord.java:139` | High |
| R5 | Birth date is `YYYYMMDD`. The driver parses dates only as `yyyyMMddHHmmss` and returns null otherwise. | 4.3.3.2 | `OrthoVisionDriver.java:27,397` | High |
| R6 | Patient ID lands in `patientIdAlternate` (field 8.1.5) rather than `practiceAssignedPatientId`; patient name is `^First^Last` (leading empty component) not Ortho's `Last^First^Middle`; ward is in 8.1.26 and is not parsed; physician is `^Name`. The LIS consumer would need different mapping for XN unless the driver normalises. | 4.3.3.2 | `OrthoVisionDriver.java:164` | Medium |
| R7 | Comment records (`C`) are discarded. XN uses them for patient comment, sample comment, and the **rerun/reflex rule names** that fired (`C\|1\|\|1^WBC HIGH\\23^Need to PLT-F analysis`). There is no `CommentRecord` model. | 4.3.3.7, 4.3.3.8 | `OrthoVisionDriver.java:88` | Medium |
| R8 | Masked values `----` (error) and `++++` (out of range) and flag values `LL`, `HH`, `>` are passed through as strings. Fine for transport, but the LIS must know them. Result status codes differ from Ortho: F none, I repeat, P rerun/reflex, N query to host. | 4.3.3.5 b), d) | n/a | Info |
| R9 | ASTM escape sequences are not decoded: `&R&` = `\`, `&E&` = `&`, `&F&` = `\|`, `&S&` = `^`. XN uses `&R&` in PNG image paths and `&E&` in research item names such as `~NEUT#&E&`. | 4.3.1.3 | driver | Low |
| R10 | Non-numeric "result" records (IP messages like `Blasts?`, `ACTION_MESSAGE_Delta`, scattergram `SCAT_WDF` raw data up to tens of KB, distribution `DIST_RBC`, research `~` items, service items) all arrive as R records. They parse without error, but the driver should classify them so the LIS can keep clinical values and discard or store images separately. | 4.3.3.5 (3.3 to 3.10) | driver | Medium |
| R11 | QC messages: patient record is `P\|1` only, sample ID starts with `QC-` or is a QC file number, action code is `Q`. Message type detection already yields `RESULT`; the LIS needs to route these differently. | 4.3.2.3, 4.3.3.4 (3) | `ServerMessageService.determineMessageType` | Info |
| R12 | `S` (scientific/reagent) records are discarded with a warning. Only sent if a service setting is enabled. Acceptable to ignore. | 4.3.3.10 | driver | Low |

### 3.3 Record building, host to IPU (`OrthoVisionDriver.build`)

What the current driver produces from model defaults, versus what the XN requires:

```
Current:  H||\^&||||||||||P|LIS2-A|20261110121919
Required: H|\^&|||||||||||E1394-97

Current:  P|1|100|||^Jim^Brown||20010820000000|M||||||||||||||||||||||||||||
Required: P|1|||100|^Jim^Brown||20010820|M|||||^Dr.1|||||||||||^^^EAST

Current:  O|1|2^1^      1234567890^B||^^^^WBC\^^^^RBC|||||||N||||||||||||||Q||||||
Required: O|1|2^1^      1234567890^B||^^^^WBC\^^^^RBC||20010807101000|||||N||||||||||||||Q

Current:  L|1|N|
Required: L|1|N
```

| # | Finding | Spec ref | Location | Severity |
|---|---|---|---|---|
| B1 | Header is wrong in two ways. `HeaderRecord.delimiters` defaults to `\|\^&` (with a leading pipe) and `buildLine` adds its own pipe, producing `H\|\|\^&`. Version is `LIS2-A` and processing ID `P`; the XN requires version `E1394-97` and leaves all other fields empty. The XN driver should emit a fixed header and ignore what the LIS sends. | 4.3.3.1 | `HeaderRecord.java:33`, `OrthoVisionDriver.java:245` | **Critical** |
| B2 | Patient ID is emitted in field 3; XN reads it from field 5. Birth date is emitted as 14 digits; XN requires 8. | 4.3.3.2 | `OrthoVisionDriver.java:263` | High |
| B3 | Order response semantics are XN-specific and are not modelled: Report Type `Q` (order exists), `Y` (no order), `X` (do not aspirate), `Z` (do not aspirate, show error); Action Code `N` or `Q`; test list as `^^^^PARAM` repeated with `\`; sample ID must be the padded 22-character value from the query; Instrument Section ID `A/S/D` for XN-2000/3000. "No order" must still be answered with a full H/P/O/L message with report type `Y`. | 4.3.3.4, 5.2.2 | driver, `OrderRecord` | High |
| B4 | Every built record ends with an extra `\|` because `buildLine` pads one field too many (`while (fields.size() <= totalFields)`). Ortho tolerated it; the XN probably will too, but it is an easy fix and removes a variable from bench testing. | 4.3.3.11 | `OrthoVisionDriver.java:429` | Low |
| B5 | Orderable parameters are a fixed XN vocabulary (WBC, RBC, HGB, HCT, MCV, MCH, MCHC, PLT, NEUT%, LYMPH%, MONO%, EO%, BASO%, NEUT#, LYMPH#, MONO#, EO#, BASO#, NRBC%, NRBC#, RDW-SD, RDW-CV, MPV, RET%, RET#, IRF, PLT-F, WPC, LWBC, SMEAR). Unknown codes are silently ignored by the IPU. The LIS or the driver needs a test-code to XN-parameter mapping. | Table 4.3.3.4.2 | LIS contract | High |

---

## 4. Core issues found during the review (affect Ortho Vision too)

| # | Finding | Location | Severity |
|---|---|---|---|
| C1 | **A dead IPU connection blocks reconnection indefinitely.** `handleNewConnection` refuses a new socket while the old handler reports `isConnected()`. That only checks `socket.isConnected() && !socket.isClosed()`, which stays true after an IPU reboot or network drop until a write fails. Reads just time out every 30 s and loop. In practice: after an analyzer restart, the service refuses it until the service is restarted. Fix: when a new connection arrives for an instrument, stop and replace the old handler, and enable TCP keep-alive on accepted sockets. | `ASTMServer.java:142`, `InstrumentConnectionHandler.handleIncomingData` | **High** |
| C2 | **Failed order sends are silently dropped.** In the handler loop the success/failure callbacks are commented out, so an order marked `PROCESSING` in `ASTMOrderMessages` is never marked `SUCCESS` or returned to `PENDING`. The retry scheduler only picks `PENDING` rows. A send that fails on NAK, timeout or ENQ collision is lost. | `InstrumentConnectionHandler.java:82-88`, `OrderMessageRepository.java:131-136` | **High** |
| C3 | **Up to 30 s latency before a queued order is sent.** The handler loop calls `receiveMessage()`, which blocks in `inputStream.read()` for the full socket timeout when the line is idle, and only checks the outgoing queue between calls. For XN real-time queries the IPU is waiting for the order response during that window. Fix: only call `receiveMessage()` when `inputStream.available() > 0`, otherwise poll the queue every 100 ms. | `InstrumentConnectionHandler.java:94`, `ASTMProtocolStateMachine.java:97` | High for XN |
| C4 | `@Value` on `InstrumentConnectionHandler` never injects because the class is constructed with `new`, so `connectionRetryDelayMinutes` is always 0. Harmless today because the code that uses it is commented out. | `InstrumentConnectionHandler.java:41` | Low |
| C5 | **Production profile cannot create a connection handler.** `application-prod.yml` instrument entries have no `driverClassName`, `exchangeName` or `resultsRoutingKey` (Spring does not merge YAML lists across profiles). `Class.forName(null)` throws on the first connection. | `application-prod.yml` | High if prod profile is used |
| C6 | Database and RabbitMQ credentials and internal IPs are committed in `application-local.yml`, `application-dev.yml`, `application-staging.yml`. Move to environment variables or an ignored override file and rotate the committed passwords. | resources | High (security) |
| C7 | `ResultQueueTestListener` consumes from `lis.instruments[0].resultQueueName` in every profile. It is a debug aid that competes with the real LIS consumer for messages. Gate it behind a profile or remove it. | `ResultQueueTestListener.java` | Medium |
| C8 | No automated tests anywhere in the repository. The parse/build behaviour above was only discoverable by running code by hand. | all modules | Medium |
| C9 | Hikari `TRACE` logging is on in `application.properties`. | `application.properties` | Low |
| C10 | Spring Boot 2.7.18 reached end of open-source support in 2023 and the build targets Java 8. Not blocking, but plan an upgrade to Boot 3.x / Java 17 once the XN work is stable. | `pom.xml` | Low |

---

## 5. Recommended plan

### Phase 0: core hardening (shared by both instruments)

1. Replace stale handlers on new connection and set `setKeepAlive(true)` on accepted sockets (C1).
2. Restore order status handling: mark `SUCCESS` on send, back to `PENDING` with `NextRetryAt` on failure (C2, C4).
3. Make the handler loop non-blocking on reads (C3).
4. Decode frames as ISO-8859-1 and compute checksums over bytes (P1). Handle incoming ENQ while waiting for ACK by yielding (P2). Raise the receive timer to 30 s (P3). ACK duplicate frames (P4).
5. Fix `buildLine` padding and the `HeaderRecord` default delimiter string (B1, B4).
6. Add `driverClassName` and queue settings to the prod profile; move secrets out of YAML (C5, C6). Gate the test listener (C7).

### Phase 1: Sysmex XN driver

1. New `SysmexXnDriver implements InstrumentDriver` in `astm-server/.../driver/impl/`.
   - Parse H (analyzer name, software version, serial from field 5), P (ID in field 5, `^First^Last`, `YYYYMMDD` DOB, ward in field 26), Q (rack, position, padded sample ID, attribute, status F/N/C), O (sample from field 4, parameters, action code, report type F/I, priority), R (parameter, dilution ratio, extended flag, value, units, flags, status, completed time), C (patient, sample and rerun/reflex rule comments), S (log only).
   - Classify R records into clinical parameter, IP message (ABNORMAL/SUSPECT), action message, positive/error judgement, image/distribution data, research/service item. Decode ASTM escapes.
   - Build H/P/O/L exactly in the shapes in section 3.3, with `Q`, `Y`, `X`, `Z` report types and the padded sample ID echoed from the query.
2. Model additions in `astm-model`: `CommentRecord`; XN-aware helpers on `QueryRecord`, `OrderRecord` and `ResultRecord` (rack, position, sample ID, attribute, parameter name, dilution ratio, units); keep Ortho behaviour unchanged.
3. Config: a second entry under `lis.instruments` (for example name `SysmexXN`, port 5000, its own order and result queues and routing key).
4. Simulator: add `sysmex-xn-test-cases.json` built from spec sections 5.1 to 5.3 (batch query, manual query, sampler query, normal results, body-fluid results, real-time QC, manual QC). Make the simulator pick the test-case file by argument.
5. Tests: JUnit tests for the driver using the spec examples verbatim, for the checksum with extended characters, and for the state machine using a socket pair.

### Phase 2: LIS contract (Infinity LIS side)

The JSON published to RabbitMQ will differ from Ortho. Agree on:

- Where the sample ID, rack and position appear for XN results and queries.
- The XN parameter code list and how LIS test codes map to it for order responses.
- How "no order" (`Y`) and "do not aspirate" (`X`/`Z`) are expressed by the LIS.
- Whether QC results, IP messages, action messages and reflex rule comments are wanted, and whether image data should be dropped before publishing.
- The response-time budget for real-time queries, so the LIS answers within the IPU's query timeout.

### Phase 3: on-site IPU settings checklist

- Host connection: TCP/IP, host IP = this service, host port = the configured port, mode **ASTM 1381-02 / 1394-97**.
- Service settings: research items off, service items off, "Only Host" items off (spec 4 note 3), image output off or `0: PNG` (not raw) unless the LIS wants scattergrams.
- Input and output priority code: 0 unless the LIS needs priority.
- "Query host computer for rerun/reflex" and "Link to IPU unit setting" as agreed with the lab.
- Confirm the analyzer models in use (XN-1000/2000/3000/XN-550 and whether SP-10/SP-50 slide maker or body-fluid mode are in use), since these change which records and fields appear.

---

## 6. Open questions

1. Which XN configuration will be connected (single XN-1000, XN-2000/3000 multi-module, standalone mode with SP-10/SP-50)? This decides whether rack/position queries and `Instrument Section ID` matter.
2. Does the lab want real-time query (sampler mode) or will orders be pushed in advance? Real-time query makes C3 and the LIS response time mandatory.
3. Does the LIS need QC results, IP flags, action messages and reflex rule names, or only the reportable parameters?
4. Which units should flow to the LIS (XN default units versus "Link to IPU unit setting")?
5. Is there an existing Sysmex connection elsewhere in the organisation whose settings can be copied?
