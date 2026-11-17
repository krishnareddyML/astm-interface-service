# Sysmex XN Integration Guide

How the service talks to a Sysmex XN series analyzer (XN-10/20, XN-1000 to XN-3100, XN-550
family) and what the Infinity LIS has to send and expect on the RabbitMQ queues.
Reference: *XN series ASTM Host Interface Specifications (North America), Revision 11*.

Driver class: `com.lis.astm.server.driver.impl.SysmexXnDriver`
Instrument name used everywhere (config, JSON, queues): `SysmexXN`

---

## 1. Connection

| Item | Value |
|---|---|
| Transport | TCP. The IPU is the client, this service is the server. |
| Port | `lis.instruments[].port` for `SysmexXN` (default 5000, the IPU default). |
| IPU host setting | **ASTM 1381-02 / 1394-97**. In "1381-95" mode the IPU sends bare records without ENQ/ACK/EOT and the service will ignore them. |
| Sessions | One session per instrument. If the IPU reconnects (reboot, network drop) the old session is closed and replaced. |
| Character set | ISO-8859-1 on the wire. Extended characters in names are preserved and checksummed correctly. |
| Timing | Host waits 15 s for ACK, 30 s between frames, yields on ENQ contention and retries after 20 s. |

### IPU service settings to agree with the lab

- Research items: **off**. Service items: **off**. Only-Host items: **off** (unless the LIS wants them; they arrive as `resultCategory = RESEARCH`).
- Image output: **off**, or `0: PNG` (file paths) if the LIS wants scattergrams. Raw image data is passed through as text but not decoded.
- Input and output priority code: `0` unless the LIS populates `priority`.
- "Query host computer for rerun/reflex": as agreed; results from reruns arrive with `actionCode = A`.
- "Link to IPU unit setting": decides which units and decimal places the IPU sends.
- Note the analyzer model: XN-2000/3000 multi-module systems honour `sectionId` (A/S/D) in order responses; XN-1500 / XN-3000 standalone with SP-10/SP-50 also send slide results (`resultCategory = SLIDE`).

---

## 2. Messages from the analyzer (published to exchange `lis.inbound.x`)

| `messageType` | Routing key | Queue |
|---|---|---|
| `RESULT` | `results.instrument.astm` | `lis.results.instrument.inbound.q` |
| `QC_RESULT` | `qc.instrument.astm` | `lis.qc.instrument.inbound.q` (never patient filing) |
| `QUERY` | `results.instrument.astm.query` | `lis.results.instrument.inbound.q` (until a query lane exists) |

A republished message (after a broker outage) has the type prefixed with `RETRY_` (for example
`RETRY_QC_RESULT`) and takes the same route.

Every message the IPU sends is stored in `AnalyzerInboundMessages`, parsed, and published as JSON
(`AstmMessage`; property names are camelCase, dates are `yyyyMMddHHmmss` strings) with headers `instrumentName`, `messageType`, `resultCount`, `orderCount`.

### 2.1 Inquiry (`messageType = QUERY`)

The IPU asks for the order for one sample. **The LIS must answer within the IPU's query timeout**
by publishing an order response (section 3) to exchange `lis.outbound.x` with routing key `orders.instrument.astm.worklist`.

```json
{
  "messageType": "QUERY",
  "instrumentName": "SysmexXN",
  "queryRecords": [{
    "sequenceNumber": 1,
    "startingRangeId": "2^1^            1234567890^B",
    "rackNumber": "2",
    "rackPosition": "1",
    "sampleId": "1234567890",
    "sampleNumberAttribute": "B",
    "requestInformationStatusCodes": "N"
  }]
}
```

| Field | Meaning |
|---|---|
| `rackNumber`, `rackPosition` | Present in sampler mode and batch (work list) inquiries. Must be echoed back. |
| `sampleId` | Trimmed sample ID (the wire value is right-aligned to 22 characters). Empty for a work-list inquiry keyed by rack/position, in which case the LIS assigns the sample ID. |
| `sampleNumberAttribute` | `M` manual, `A` auto-assigned (barcode read error, ID starts with `ERR`), `B` barcode, `C` host assigned. Echo it back. |
| `requestInformationStatusCodes` | `F` real-time manual analysis or batch inquiry, `N` sampler initial analysis, `C` sampler rerun/reflex. |

### 2.2 Analysis results (`messageType = RESULT`) and QC (`messageType = QC_RESULT`)

```json
{
  "messageType": "RESULT",
  "patientRecord": {
    "practiceAssignedPatientId": "100",
    "patientName": "Brown^Jim",
    "birthDate": "20010820000000",
    "patientSex": "M",
    "attendingPhysicianId": "^Dr.1",
    "location": "WEST"
  },
  "orderRecords": [{
    "specimenId": "1234567890",
    "instrumentSpecimenId": "2^1^            1234567890^B",
    "rackNumber": "2", "rackPosition": "1", "sampleNumberAttribute": "B",
    "universalTestId": "^^^^WBC\\^^^^RBC\\^^^^HGB",
    "actionCode": "N",
    "reportTypes": "F",
    "sectionId": ""
  }],
  "resultRecords": [
    { "parameterName": "WBC", "dilutionRatio": "1", "dataValue": "7.81", "units": "10*3/uL",
      "resultAbnormalFlags": "N", "resultStatus": "F", "resultCategory": "CLINICAL",
      "dateTimeTestCompleted": "20010806120000" },
    { "parameterName": "RBC", "dataValue": "----", "resultAbnormalFlags": "A", "resultCategory": "CLINICAL" },
    { "parameterName": "Blasts?", "dataValue": "100", "resultAbnormalFlags": "A", "resultCategory": "IP_SUSPECT" },
    { "parameterName": "ACTION_MESSAGE_Delta", "resultAbnormalFlags": "A", "resultCategory": "ACTION_MESSAGE" },
    { "parameterName": "SCAT_DIFF", "dataValue": "PNG\\20010806\\2001_08_06_12_00_1234567890_DIFF.PNG", "resultCategory": "IMAGE" }
  ],
  "commentRecords": [
    { "scope": "PATIENT", "commentText": "Patient Comments" },
    { "scope": "ORDER", "commentText": "Sample Comments" },
    { "scope": "RESULT", "commentText": "1^WBC HIGH\\23^Need to PLT-F analysis",
      "ruleReferences": [ { "ruleNumber": "1", "ruleName": "WBC HIGH" }, { "ruleNumber": "23", "ruleName": "Need to PLT-F analysis" } ] }
  ]
}
```

| Field | Meaning |
|---|---|
| `patientName` | Normalised to `Last^First` (the IPU sends `^First^Last`). |
| `orderRecords[].actionCode` | `N` initial/manual, `A` rerun or reflex, `Q` QC. |
| `orderRecords[].reportTypes` | `F` normal, `I` repeat analysis. |
| `resultRecords[].resultCategory` | `CLINICAL`, `QC`, `IP_ABNORMAL`, `IP_SUSPECT`, `ACTION_MESSAGE`, `JUDGEMENT`, `IMAGE`, `DISTRIBUTION`, `RESEARCH`, `SLIDE`, `OTHER`. Report only `CLINICAL` (and `QC` if wanted). |
| `dataValue` | `----` analysis or hardware error, `++++` out of range. `IP_SUSPECT` carries a Q-flag grade 0 to 300. |
| `resultAbnormalFlags` | `L` low, `H` high, `>` out of linearity, `N` normal, `A` error/flag set, `W` low reliability, `LL` / `HH` panic. |
| `resultStatus` | `F` none, `I` repeat, `P` rerun or reflex, `N` query to host. |
| `dilutionRatio` | `1` manual/sampler, `5` capillary. |
| `commentRecords[].scope` | `PATIENT`, `ORDER`, or `RESULT` (rerun/reflex rules that fired). |

QC messages (`QC_RESULT`) have an empty patient record, a sample ID starting with `QC-` (real-time QC)
or a QC file number (manual output), and every value classified `QC`. Abnormal flags are not
sent for QC data.

---

## 3. Order response from the LIS (consumed from `lis.orders.instrument.astm.outbound.q`)

Publish one message per inquiry. The service persists it in `AnalyzerOutboundOrders`, sends it on
the open session, and marks it `SUCCESS`, or retries it (`PENDING` with `NextRetryAt`) when the
IPU is disconnected, busy, or did not acknowledge, up to `lis.messaging.retry.max-attempts`.

```json
{
  "instrumentName": "SysmexXN",
  "patientRecord": {
    "practiceAssignedPatientId": "100",
    "patientName": "Brown^Jim",
    "birthDate": "20010820000000",
    "patientSex": "M",
    "attendingPhysicianId": "Dr.1",
    "location": "WEST"
  },
  "commentRecords": [ { "scope": "PATIENT", "commentText": "Patient Comments" } ],
  "orderRecords": [{
    "rackNumber": "2",
    "rackPosition": "1",
    "specimenId": "1234567890",
    "sampleNumberAttribute": "B",
    "universalTestId": "WBC\\RBC\\HGB\\HCT\\MCV\\MCH\\MCHC\\PLT\\NEUT%\\LYMPH%\\MONO%\\EO%\\BASO%\\NEUT#\\LYMPH#\\MONO#\\EO#\\BASO#\\RDW-SD\\RDW-CV\\MPV\\RET%\\RET#\\IRF",
    "requestedDateTime": "20010807101000",
    "actionCode": "N",
    "reportTypes": "Q"
  }]
}
```

(Same `AstmMessage` model and camelCase property names as the results; dates as `yyyyMMddHHmmss` strings.)

Which produces on the wire:

```
H|\^&|||||||||||E1394-97
P|1|||100|^Jim^Brown||20010820|M|||||^Dr.1|||||||||||^^^WEST
C|1||Patient Comments
O|1|2^1^            1234567890^B||^^^^WBC\^^^^RBC\...\^^^^IRF||20010807101000|||||N||||||||||||||Q
L|1|N
```

Rules:

| Field | Rule |
|---|---|
| `rackNumber`, `rackPosition`, `sampleNumberAttribute` | Copy from the inquiry. |
| `specimenId` | Plain sample ID; the driver pads it to 22 characters. For a work-list inquiry (no sample ID), assign one here. |
| `universalTestId` | Parameter names separated by `\` (or `,`). Raw `^^^^WBC\^^^^RBC` is also accepted. Allowed names: WBC, RBC, HGB, HCT, MCV, MCH, MCHC, PLT, NEUT%, LYMPH%, MONO%, EO%, BASO%, NEUT#, LYMPH#, MONO#, EO#, BASO#, NRBC%, NRBC#, RDW-SD, RDW-CV, MPV, RET%, RET#, IRF, PLT-F, WPC, LWBC, SMEAR. Unknown names are silently ignored by the IPU. |
| `reportTypes` | `Q` order exists (default when parameters are given), `Y` no order (default when none are given; the IPU runs its default profile), `X` do not aspirate, `Z` do not aspirate and show an error. |
| `actionCode` | `N` (default) or `Q` for QC material (forced when the sample ID starts with `QC-`). |
| `sectionId` | XN-2000/3000 only: `A` any analyzer, `S` same module as the initial run, `D` different module. |
| `priority` | Only used if the IPU service setting "Input and output priority code" is 1 or 3. |
| `patientRecord` | Optional. When omitted a bare `P|1` is sent. Names in `Last^First` form. |
| `commentRecords` | Optional; `scope` `PATIENT` (max 100 chars) or `ORDER` (max 40 chars). |

**No order:** still publish a response with the echoed sample key and `reportTypes = "Y"` (or `X`).
If nothing is sent the IPU waits for its query timeout before running the default profile.

**Known ambiguity to confirm on the bench:** the specification table places the ward in P field
8.1.26 but every example in the document shows it one field earlier. The driver reads both
positions and writes the example position. Check which one the IPU displays.

---

## 4. Testing without the analyzer

```bash
# server (needs SQL Server and RabbitMQ reachable; DB_PASSWORD in the environment)
cd astm-server && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
# simulator against the SysmexXN listener with the XN spec messages
java -jar instrument-simulator/target/instrument-simulator.jar localhost 5000 sysmex-xn-test-cases.json
```

Publish `instrument-simulator/src/main/resources/test-messages/sysmex-xn-order-response.json`
to exchange `lis.outbound.x` with routing key `orders.instrument.astm.worklist` while the simulator is in "Listen for Orders" mode to see the order
response the IPU would receive.

Automated coverage: `mvn test` runs the driver against the spec's own example messages
(`SysmexXnDriverTest`), the E1381 state machine over a real socket pair, and the connection
handler with a fake instrument.
