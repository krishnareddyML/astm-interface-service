package com.lis.astm.server.driver.impl;

import lombok.Value;

/**
 * The Sysmex XN specimen key used in Q 12.1.3, O 9.4.3 / 9.4.4 and S 14.1.11:
 * {@code RackNo^RackPosition^SampleIdNo^SampleNoAttribute}.
 *
 * <p>The sample ID is a 22-character field, right-aligned and space padded on the wire.
 * The host must echo the padded value and the rack/position exactly as received in the inquiry.
 */
@Value
public class SysmexXnSpecimenId {

    public static final int SAMPLE_ID_WIDTH = 22;

    String rackNumber;
    String rackPosition;
    /** Trimmed sample ID. */
    String sampleId;
    /** M manual, A auto-assigned by analyzer, B barcode, C host assigned. */
    String attribute;

    /** Parses the composite field; missing components become empty strings. */
    public static SysmexXnSpecimenId parse(String composite) {
        if (composite == null) composite = "";
        String[] c = composite.split("\\^", -1);
        return new SysmexXnSpecimenId(
                component(c, 0),
                component(c, 1),
                component(c, 2).trim(),
                component(c, 3));
    }

    private static String component(String[] c, int i) {
        return i < c.length && c[i] != null ? c[i] : "";
    }

    public boolean isEmpty() {
        return rackNumber.isEmpty() && rackPosition.isEmpty() && sampleId.isEmpty() && attribute.isEmpty();
    }

    /** True for QC material: sample number starts with "QC-" (real-time QC) or the action code is Q. */
    public boolean isQcSample() {
        return sampleId.startsWith("QC-");
    }

    /** True for a barcode read error number ("ERR" + 12 digits). */
    public boolean isBarcodeError() {
        return sampleId.startsWith("ERR");
    }

    /** Formats the composite as the IPU expects it: sample ID right-aligned to 22 characters. */
    public String format() {
        return nz(rackNumber) + "^" + nz(rackPosition) + "^" + padSampleId(sampleId) + "^" + nz(attribute);
    }

    /** Right-aligns a sample ID to the 22-character wire width (values already that wide or wider are unchanged). */
    public static String padSampleId(String sampleId) {
        String id = sampleId == null ? "" : sampleId;
        if (id.isEmpty() || id.length() >= SAMPLE_ID_WIDTH) return id;
        StringBuilder sb = new StringBuilder(SAMPLE_ID_WIDTH);
        for (int i = id.length(); i < SAMPLE_ID_WIDTH; i++) sb.append(' ');
        return sb.append(id).toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
