package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ASTM E1394 Comment Record (C). A comment belongs to the record that precedes it
 * (patient, order or result); {@link #scope} records which one.
 *
 * <p>Sysmex XN usage (spec 4.3.3.7 - 4.3.3.9):
 * <ul>
 *   <li>after P: free-text patient comment (up to 100 characters)</li>
 *   <li>after O: free-text sample comment (up to 40 characters)</li>
 *   <li>after the R records: rerun/reflex rules that fired, as {@code ruleNo^ruleName} repeated with '\'</li>
 *   <li>after H (standalone SP-10/SP-50 only): consumable replacement code</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentRecord {

    public enum Scope { MESSAGE, PATIENT, ORDER, RESULT }

    /** Sysmex rerun/reflex rule reference carried in a comment record. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleReference {
        @JsonProperty("ruleNumber")
        private String ruleNumber;
        @JsonProperty("ruleName")
        private String ruleName;
    }

    @JsonProperty("recordType")
    @Builder.Default
    private String recordType = "C";

    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber;

    /** Field 11.1.3: I (instrument), L (LIS/computer system) or empty. */
    @JsonProperty("commentSource")
    private String commentSource;

    /** Field 11.1.4: raw comment text, escape sequences already decoded. */
    @JsonProperty("commentText")
    private String commentText;

    /** Field 11.1.5: G (generic), T (test), P (positive), N (negative), I (instrument) or empty. */
    @JsonProperty("commentType")
    private String commentType;

    /** Which preceding record this comment belongs to. */
    @JsonProperty("scope")
    @Builder.Default
    private Scope scope = Scope.MESSAGE;

    /** Parsed rerun/reflex rule references when the comment carries them (Sysmex XN). */
    @JsonProperty("ruleReferences")
    @Builder.Default
    private List<RuleReference> ruleReferences = new ArrayList<>();

    public void addRuleReference(String ruleNumber, String ruleName) {
        if (ruleReferences == null) ruleReferences = new ArrayList<>();
        ruleReferences.add(new RuleReference(ruleNumber, ruleName));
    }

    public boolean hasRuleReferences() {
        return ruleReferences != null && !ruleReferences.isEmpty();
    }
}
