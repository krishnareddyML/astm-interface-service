# ORTHO VISION® — ASTM E1394 Record Specifications

**Records covered:** H (Header), L (Trailer), P (Patient), O (Order), Q (Request/Query), R (Result), M (Result/Well)  
**Prepared:** 2025-09-24

This document consolidates the fields **supported by the ORTHO VISION® system** (non‑shaded rows in vendor tables). It follows ASTM E1394 sections noted per record.

## Table of Contents
- Legend
- H — Header Record
- L — Trailer Record
- P — Patient Record
- O — Order Record
- Q — Request / Query Record
- R — Result Record
- M — Result Record

## Legend

- **D (Data status):** `R` = Required, `X` = Required for Xmatch, `O` = Optional, `N` = Never used, `-` = N/A  
- **U (Usage / Sent by instrument):** `A` = Always sent, `x` = Always sent for Xmatch, `S` = Sometimes sent, `N` = Never sent, `-` = N/A  
- **R (Repeatability):** `Y` = Field can repeat, `N` = Field does not repeat


# ORTHO VISION® — ASTM E1394 Record Specifications (H & L)

_Source: specs provided in images. This file captures only the fields supported by the ORTHO VISION® system (non‑shaded rows in the images)._

## Legend

- **D (Data status):** `R` = Required, `X` = Required for Xmatch, `O` = Optional, `N` = Never used, `-` = N/A  
- **U (Usage / Sent by instrument):** `A` = Always sent, `x` = Always sent for Xmatch, `S` = Sometimes sent, `N` = Never sent, `-` = N/A  
- **R (Repeatability):** `Y` = Field can repeat, `N` = Field does not repeat

---

## H — Header Record (`H`)

Defined in ASTM E1394 §7.1. The ORTHO VISION® system supports the following fields.

| #   | D | U | R | Field                | Notes |
|-----|---|---|---|----------------------|-------|
| 1   | R | A | N | **Record Type ID**   | Must be `"H"` (case‑insensitive `H` or `h`). |
| 2   | R | A | N | **Field Delimiters** | Must be `"|\^&"` → these are the **Field**, **Repeat**, **Component**, and **Escape** delimiters. Delimiters are fixed on upload; on download, all delimiters may vary. |
| 3   | N | N | N | **Message Control ID** | *Unused*. |
| 4   | N | N | N | **Access Password**    | *Unused*. |
| 5   | O | A | N | **Sender Name/ID**     | Uses the **System Name** from system configuration. |
| 5.1 | O | A | N | —                      | Manufacturer name; literal `"OCD"`. |
| 5.2 | R | A | N | —                      | Product name; literal `"VISION"`. |
| 5.3 | R | A | N | —                      | Software Version. |
| 5.4 | O | A | N | —                      | Instrument ID. |
| 6   | N | N | N | **Sender Street Address** | *Unused*. |
| 7   | N | N | N | **Reserved Field**        | *Unused*. |
| 8   | N | N | N | **Sender Telephone Number** | *Unused*. |
| 9   | N | N | N | **Characteristics of Sender** | *Unused*. |
| 10  | N | N | N | **Receiver ID** | *Unused*. |
| 11  | N | N | N | **Comment** | *Unused*. |
| 12  | N | A | N | **Processing ID** | Value `"P"` → Production message. |
| 13  | N | A | N | **Version Number** | ASTM protocol version `"LIS2-A"`. |
| 14  | O | A | N | **Date and Time of Message** | Transmission timestamp in `YYYYMMDDHHMMSS`. |


## L — Trailer Record (`L`)

Defined in ASTM E1394 §13.1.

| #  | D | U | R | Field              | Notes |
|----|---|---|---|--------------------|-------|
| 1  | R | A | N | **Record Type ID** | Must be `"L"` (case-insensitive `L` or `l`). |
| 2  | N | N | N | **Sequence Number** | *Unused*. |
| 3  | N | N | N | **Termination Code** | *Unused*. |

---

### Notes & Assumptions
- Rows marked *Unused* are not used by ORTHO VISION® (shaded in the source tables). They’re listed here for completeness.
- Where the images specified literal values (e.g., `"OCD"`, `"VISION"`, `"P"`, `"LIS2‑A"`), those literals are preserved verbatim.
- This document currently includes **Header (H)** and **Trailer (L)** records only. Additions for **Patient (P)**, **Order (O)**, **Result (R)**, etc., can be appended in the same format when specs are provided.


## P — Patient Record (`P`)

Defined in ASTM E1394 §8.1. The ORTHO VISION® system supports the following (non‑shaded) fields.

| #  | D | U | R | Field                             | Notes |
|----|---|---|---|-----------------------------------|-------|
| 1  | R | A | N | **Record Type ID**                | Must be `"P"` (case‑insensitive `P` or `p`). |
| 2  | O | A | N | **Sequence number**               | Patient sequence number. Set to **1** for the first patient record, **2** for the second, and so on. |
| 3  | O | P | N | **Practice Assigned Patient ID**  | = Patient ID. If present, the unique identifier for the patient. May contain alphanumeric, symbols, and embedded blanks up to 20 characters. Leading zeros are retained; IDs composed only of blanks are treated as **NULL**. Leading/trailing blanks are stripped; embedded blanks are preserved. |
| 4  | N | N | N | **Lab Assigned Patient ID**       | *Unused*. |

### P.5 — Additional Patient IDs
| #   | D | U | R | Component         | Notes |
|-----|---|---|---|-------------------|-------|
| 5.1 | O | S | N | Patient ID No. 3  | = National ID. |
| 5.2 | O | S | N | —                 | = Medical Record. |
| 5.3 | O | S | N | —                 | = Other ID. |

### P.6 — Patient Name (components)
| #   | D | U | R | Component   | Notes |
|-----|---|---|---|-------------|-------|
| 6.1 | O | S | N | Patient Name| = Last Name. |
| 6.2 | O | S | N | —           | = First Name. |
| 6.3 | O | S | N | —           | = Middle Initial. |
| 6.4 | N | N | N | —           | = Suffix. *(Unused)* |
| 6.5 | N | N | N | —           | = Title. *(Unused)* |

| #  | D | U | R | Field                          | Notes |
|----|---|---|---|--------------------------------|-------|
| 7  | O | P | N | **Mother's Maiden Name**       | = Mother's Maiden Surname. May be required to distinguish patients with same birth date and last name. |
| 8  | O | P | N | **Birth date**                 | Actual birth date: `YYYYMMDD`, `YYYYMMDDHHMM`, or `YYYYMMDDHHMMSS`. **Upload format:** `YYYYMMDDHHMMSS`. |
| 9  | O | S | N | **Patient Sex**                | Allowed: `M`, `F`, `U`. If NULL is provided, system uploads `U`. |
| 10 | N | N | N | **Patient Race/Ethnic Origin** | *Unused*. |
| 11 | N | N | N | **Patient Address**            | *Unused*. |
| 12 | N | N | N | **Reserved Field**             | *Unused*. |
| 13 | N | N | N | **Patient Telephone Number**   | *Unused*. |

### P.14 — Attending Physician (single physician allowed)
| #    | D | U | R | Component     | Notes |
|------|---|---|---|---------------|-------|
| 14   | O | P | N | Attending Physician | The system allows only **one** physician. |
| 14.1 | O | P | N | —             | Physician ID. |
| 14.2 | O | P | N | —             | Last Name. |
| 14.3 | O | P | N | —             | First Name. |
| 14.4 | O | P | N | —             | Middle Initial. |

| #  | D | U | R | Field                         | Notes |
|----|---|---|---|-------------------------------|-------|
| 15 | O | P | N | **Patient’s Birth Name**      | = Patient’s Birth Surname. May be required to distinguish patients with same birth date and last name. |
| 16 | N | N | N | **Special Field 2**           | *Unused*. |
| 17 | N | N | N | **Patient Height**            | *Unused*. |
| 18 | N | N | N | **Patient Weight**            | *Unused*. |
| 19 | N | N | N | **Patient's Diagnosis**       | *Unused*. |
| 20 | N | N | N | **Patient Active Medications**| *Unused*. |
| 21 | N | N | N | **Patient's Diet**            | *Unused*. |
| 22 | N | N | N | **Practice Field 1**          | *Unused*. |
| 23 | N | N | N | **Practice Field 2**          | *Unused*. |
| 24 | N | N | N | **Admission and Discharge Dates** | *Unused*. |
| 25 | N | N | N | **Admission Status**          | *Unused*. |
| 26 | N | N | N | **Location**                  | *Unused*. |
| 27 | N | N | N | **Nature of Alternative Diagnosis Code** | *Unused*. |
| 28 | N | N | N | **Alternative Diagnosis Code**| *Unused*. |
| 29 | N | N | N | **Patient Religion**          | *Unused*. |
| 30 | N | N | N | **Marital Status**            | *Unused*. |
| 31 | N | N | N | **Isolation Status**          | *Unused*. |
| 32 | N | N | N | **Language**                  | *Unused*. |
| 33 | N | N | N | **Hospital Service**          | *Unused*. |
| 34 | N | N | N | **Hospital Institution**      | *Unused*. |
| 35 | N | N | N | **Dosage Category**           | *Unused*. |



## O — Order Record (`O`)

Defined in ASTM E1394 §9.4. The ORTHO VISION® system supports the following (non‑shaded) fields.

> **Additional legend for D column here (specific to O):**  
> `X` = Required when **Crossmatch** is in the profile; `Q` = Required for **QC orders** (when Action Code = `Q`).  
> Rows marked with `*` include extra conditions explained in the **Notes** of that row.

| #   | D  | U | R | Field                          | Notes |
|-----|----|---|---|--------------------------------|-------|
| 1   | R  | A | N | **Record Type ID**             | Must be `"O"` (case‑insensitive `O` or `o`). |
| 2   | R  | A | N | **Sequence Number**            | Starts at **1**. Increments by 1 for each **result** within an order. Reset with each new **Patient (P)** record. |
| 3   | R  | A | Y | **Specimen ID**                | = Sample ID. A maximum of **two** Sample IDs can be identified. **Sample ID O 3.1** pairs with **Sample Type O 16.1** and **Sample ID O 3.2** pairs with **Sample Type O 16.2** (where `.1` is the first repeat and `.2` the second). |
| 4   | N  | N | N | **Instrument Specimen ID**     | *Unused*. |
| 5   | R  | A | N | **Universal Test ID**          | — |
| 5.1 | R  | A | Y | — **Profile Name**             | = Profile name. Profiles are configured on the instrument; **case‑sensitive** and must be known to the system. **Upload:** only one profile per Order. **Download:** only one donor list per Order. **Crossmatch:** if the profile contains a cross‑match test, then **5.2** and one or more **(5.a, 5.b) pairs** are required. |
| 5.2 | X  | S | N | — **Number (N) of Donor Samples** | = number of donor samples **or NULL**. Required **if** donor samples are included. A crossmatch may contain zero or more donor samples; if none are included the order is held until a donor sample is manually added. |
| 5.a | X* | P | N | — **nᵗʰ Donor Specimen ID**   | SampleID of the *n*ᵗʰ donor. `a = 2*n + 1`, where `1 ≤ n ≤ N` is the donor index. *Required for each additional donor* (see **5.1**, crossmatch). |
| 5.b | X* | P | N | — **Sample type of nᵗʰ Donor** | Sample type of the *n*ᵗʰ donor. `b = 2*n + 2`, where `1 ≤ n ≤ N`. *Required for each additional donor* (see **5.1**, crossmatch). |
| 5.c | Q  | N | N | — **Number (M) of Card Lots to use** | Number of subsequent **Card ID/Lot** components. `c = 2*n + 3`, where `1 ≤ n ≤ N` is the donor index. If `0` or `NULL`, the system automatically determines which lot(s) to use. **Ignored for non‑QC orders** (Action Code ≠ `Q`). |
| 5.d | Q* | N | N | — **mᵗʰ Card ID**             | Card ID. `d = 2*n + 2*m + 2`, where `1 ≤ m ≤ M` is the mᵗʰ Card Lot to specify, `n` is the donor index. *Required for each Card.* |
| 5.e | Q* | N | N | — **mᵗʰ Card Lot ID**         | Card Lot number. `e = 2*n + 2*m + 3`, where `1 ≤ m ≤ M`, `n` is the donor index. *Required for each Card.* |
| 5.f | Q  | N | N | — **Number (P) of Reagent Lots to use** | Number of subsequent **Reagent ID/Lot** components. `f = 2*n + 2*m + 4`, where `m` is the number of Card Lots specified and `n` is the donor index. If `0` or `NULL`, the system determines which lot(s) to use. **Ignored for non‑QC orders** (Action Code ≠ `Q`). |
| 5.g | Q  | N | N | — **pᵗʰ Reagent ID**          | Reagent ID. `g = 2*N + 2*M + 2*p + 3`, where `1 ≤ p ≤ P`; `N` = number of donor samples; `M` = number of Card IDs/Lots. *Required for each reagent.* |
| 5.h | Q  | N | N | — **pᵗʰ Reagent Lot ID**      | Reagent Lot number. `h = 2*N + 2*M + 2*p + 4`, where `1 ≤ p ≤ P`. *Required for each reagent.* |
| 6   | O  | A | N | **Priority**                   | Allowed: `NULL`, `S`, `A`, `R`, `C`, `P`, `N`. In LIS2‑A §9.4.6, `N` is the same as `R`. `NULL = R = N = C = P` (routine). `S = A` (STAT). |
| 7   | O  | A | N | **Requested/Order Date and Time** | `YYYYMMDDHHMMSS`. |
| 8   | O  | N | Y | **Specimen Collection Date and Time** | Date/time specimen was collected (`YYYYMMDDHHMMSS`). **On Upload:** `NULL`. |
| 9   | N  | N | N | **Collection End Time**        | *Unused*. |
| 10  | N  | N | N | **Collection Volume**          | *Unused*. |
| 11  | N  | N | N | **Collector ID**               | *Unused*. |
| 12  | R  | N | N | **Action Code**                | One of `C`, `N`, `A`, `Q`. `N = A`. Meanings: `C` cancel described order; `N/A` add profiles on a known sample / new profiles on an unknown sample; `Q` treat specimen as **QC** test specimen. `NULL` on Result Upload. |
| 13  | N  | N | N | **Danger Code**                | *Unused*. |
| 14  | O  | S | Y | **Relevant Clinical Info — Expected Test Results** | **Ignored for non‑QC orders** (Action Code ≠ `Q`). Test names are **case‑sensitive** and must be known by the system. Overwrites predefined expected test results. Must be set for each performed test of **Non‑Ortho QC Profile**. Expected result must be provided for each executed test in this order. |
| 14.1| O  | S | N | — **Test‑Name**                | Name of an Analysis type. |
| 14.2| O  | S | N | — **Expected Result**          | Expected result value. |
| 15  | N  | N | N | **Date/Time Specimen Received**| *Unused*. |
| 16  | R  | A | Y | **Specimen Descriptor**        | = Sample type. Exactly **one** Specimen Descriptor per Specimen ID. **O 3.1 ↔ O 16.1**, **O 3.2 ↔ O 16.2** (first/second repeats correspond). |
| 17  | N  | N | N | **Ordering Physician**         | *Unused*. |
| 18  | N  | N | N | **Physician's Phone Number**   | *Unused*. |
| 19  | O  | N | N | **User Field 1**               | `NULL` or `S`. If `S`: save all the Cards for all profiles for **manual review**. |
| 20  | N  | S | N | **User Field 2**               | Comment. Error message if this order could not be processed by the ORTHO VISION® system. |
| 21  | N  | N | N | **Laboratory Field 1**         | *Unused*. |
| 22  | N  | N | N | **Laboratory Field 2**         | *Unused*. |
| 23  | N  | A | N | **Date/Time Results Reported or Last Modified** | `YYYYMMDDHHMMSS`. |
| 24  | N  | N | N | **Instrument Charge**          | *Unused*. |
| 25  | N  | N | N | **Instrument Section ID**      | *Unused*. |
| 26  | N  | A | N | **Report Types**               | One of `P`, `F`, `R`, `X`. `P` partial; `F` final; `R` repeat results; `X` order cancelled on the instrument. |
| 27  | N  | N | N | **Reserved Field**             | *Unused*. |
| 28  | O  | S | N | **Location/Ward of Specimen Collection** | Collection location where specimen was collected. |
| 29  | N  | N | N | **Nosocomial Infection Flag**  | *Unused*. |
| 30  | N  | N | N | **Specimen Service**           | *Unused*. |
| 31  | N  | N | N | **Specimen Institution**       | *Unused*. |



## Q — Request / Query Record (`Q`)

Defined in ASTM E1394 §12.1. The ORTHO VISION® system supports the following (non‑shaded) fields.

| #   | D | U | R | Field                               | Notes |
|-----|---|---|---|-------------------------------------|-------|
| 1   | - | A | N | **Record Type ID**                  | Must be `"Q"` (case‑insensitive `Q` or `q`). |
| 2   | - | A | N | **Sequence number**                 | Initial value is **1**; reset for each new message. |
| 3   | - | - | N | **Starting Range ID Number**        | — |
| 3.1 | - | N | N | **Computer system patient ID**      | *Unused*. |
| 3.2 | - | A | N | **Computer system specimen ID**     | = Computer System **Sample ID**. The Sample ID of interest; only **one** allowed per record. |
| 4   | - | N | N | **Ending Range ID Number**          | *Unused*. |
| 5   | - | N | N | **Universal Test ID**               | *Unused*. |
| 6   | - | N | N | **Nature of Request Time Limits**   | *Unused*. |
| 7   | - | N | N | **Beginning Request Results Date and Time** | *Unused*. |
| 8   | - | N | N | **Ending Request Results Date and Time**   | *Unused*. |
| 9   | - | N | N | **Requesting Physician Name**       | *Unused*. |
| 10  | - | N | N | **Requesting Physician Phone Number** | *Unused*. |
| 11  | - | N | N | **User Field 1**                    | *Unused*. |
| 12  | - | N | N | **User Field 2**                    | *Unused*. |
| 13  | - | A | N | **Request Information Status Codes**| Allowed value: `"O"` → request **test orders and demographics only**. |



## R — Result Record (`R`)

A result record is transmitted to the LIS for **each executed test**. Defined in ASTM E1394 §10.1. The ORTHO VISION® system supports the following (non‑shaded) fields.

| #   | D | U | R | Field                            | Notes |
|-----|---|---|---|----------------------------------|-------|
| 1   | - | A | N | **Record Type ID**               | Must be `"R"` (case‑insensitive `R` or `r`). |
| 2   | - | A | N | **Sequence number**              | Initial value is **1**, reset for each **new order**; maximum length is unlimited. |
| 3   | - | A | N | **Test ID**                      | — |
| 3.1 | - | A | N | — **Analysis**                   | = Analysis type. |
| 3.2 | - | S | N | — **Donor Specimen ID**          | Included only if **crossmatch** test. = Sample ID of donor. The system writes **one R record per reaction**. *Analysis results returned by the ORTHO VISION® system are configurable and may change.* |
| 4   | - | A | N | **Data or Measurement Value**    | = Analysis Result. |
| 5   | - | N | N | **Units of Measurement Value**   | *Unused*. |
| 6   | - | N | N | **Reference Ranges**             | *Unused*. |
| 7   | - | S | Y | **Result Abnormal Flags**        | Allowed: `NULL`, `M`, `Q`, `S`, `T`, `X`, `E`, `I`, `F`, `C`, `P`, `NA`, `R`, `N`. Meanings — **M**: result entered/modified manually; **Q**: out of QC (overdue QC on a reagent/Card); **S**: out of maintenance service (overdue task); **T**: test mode (simulated); **X**: imaging system errors; **E**: temperature/humidity out of range; **I**: indeterminate (no match from AD); **F**: user‑defined protocol; **C**: discrepant result; **P**: above/below positive reaction threshold; **NA**: result expired; **R**: result expired for a manually entered result; **N**: negative reaction threshold. |
| 8   | - | N | N | **Nature of Abnormality Testing**| *Unused*. |
| 9   | - | A | N | **Result Status**                | One of `F`, `R`, `X`. `F` = final result; `R` = repeat result; `X` = result rejected or cancelled. |
| 10  | - | N | N | **Date of Change in Instrument Normative Values or Units** | *Unused*. |
| 11  | - | S | N | **Operator Identification**      | When **Include Operator** is **No** (default). = Operator ID of the person who accepts the test. Returns `"Automatic"` for tests accepted automatically. |
| 11.1| - | S | N | — **Instrument Operator**        | When **Include Operator** is **Yes**. Logged‑in operator who loaded the samples/reagents. |
| 11.2| - | S | N | — **Verifier**                   | When **Include Operator** is **Yes**. Operator who accepted the test. Returns `"Automatic"` for tests accepted automatically. |
| 12  | - | N | N | **Date/Time Test Started**       | *Unused*. |
| 13  | - | A | N | **Date/Time Test Completed**     | Date/Time of result `YYYYMMDDHHMMSS`. |
| 14  | - | A | N | **Instrument Identification**    | = Number. |
| 15  | - | S | N | **Test Name**                    | When enabled, contains the name of the test associated with the result record. |



## M — Result Record (`M`)

Defined in ASTM E1394 §15.1. Used to transmit **well-level** result data and images. The ORTHO VISION® system supports the following (non‑shaded) fields.

| #   | D | U | R | Field                       | Notes |
|-----|---|---|---|-----------------------------|-------|
| 1   | - | A | N | **Record Type ID**          | Must be `"M"` (case‑insensitive `M` or `m`). |
| 2   | - | A | N | **Sequence number**         | `1` for initial order, then reset for each new order; maximum length is unlimited. |
| 3   | - | A | N | **Result Well Name**        | Name of the test well. **For crossmatch:** Donor Sample ID. |

### M.4 — Card Information
| #   | D | U | R | Component              | Notes |
|-----|---|---|---|------------------------|-------|
| 4.1 | - | A | N | **Type of Card**       | Type of Card. |
| 4.2 | - | A | N | **Number of the well** | Values `1..6`. |
| 4.3 | - | A | N | **Card ID Number**     | Serial # as encoded in the barcode. |
| 4.4 | - | A | N | **Card Lot Number**    | — |
| 4.5 | - | A | N | **Card Expiration Date** | `YYYYMMDDHHMMSS`. |
| 4.6 | - | S | N | **Mono Image File Name**  | File name of the Card image used to determine this result. Image data is stored in the **Shared Images Folder** even when the message is not transferred through the shared‑folder interface. |
| 4.7 | - | S | N | **Color Image File Name** | File name of the Card image used to determine this result. Image data is stored in the **Shared Images Folder** even when the message is not transferred through the shared‑folder interface. |

### M.5 — Reagent Information (repeating)
| #   | D | U | R | Component               | Notes |
|-----|---|---|---|-------------------------|-------|
| 5   | - | A | Y | **Reagent Information** | Group (repeats per reagent). |
| 5.1 | - | A | N | **Reagent Name**        | Reagent Name. |
| 5.2 | - | A | N | **Reagent Lot Number**  | — |
| 5.3 | - | A | N | **Reagent Expiration Date** | `YYYYMMDDHHMMSS`. |

### M.6 — Result Details
| #   | D | U | R | Component                   | Notes |
|-----|---|---|---|-----------------------------|-------|
| 6.1 | - | A | N | **Final Result or Error**   | Use **Grade** codes from **Table — Results or Error** below. |
| 6.2 | - | A | N | **Manual Correction Flag**  | `M` = manual correction; `A` = automatic correction. |
| 6.3 | - | S | N | **Read Result or Error**    | The initially read grade code (from table below). |
| 6.4 | - | S | N | **Operator ID**             | Operator ID of the user who made the correction. |

### Table — Results or Error (Grade → Meaning)

**Reaction grades**

| Grade | Meaning |
|------:|---------|
| 0  | `0` reaction |
| 10 | `1+` reaction |
| 20 | `2+` reaction |
| 30 | `3+` reaction |
| 40 | `4+` reaction |

**Error/condition grades**

| Grade | Meaning |
|------:|---------|
| -90  | Well Not Found |
| -95  | Wrong liquid level |
| -100 | Light too low |
| -101 | Light too high |
| -110 | Contrast interference |
| -111 | Empty column |
| -112 | Too few cells |
| -113 | Too many cells |
| -115 | Mixed field |
| -116 | Indeterminate |
| -117 | Fibrin |
| -118 | Bubble |
| -119 | Cells detected |
| -201 | Focus error |
| -203 | Splash |
| -206 | Tilt error |
| -207 | Rotation error |
| -208 | Skew error |
| -209 | Well fluid error |
| -256 | Card not detected |
| -260 | Wrong position |
| -999 | Not applicable |

