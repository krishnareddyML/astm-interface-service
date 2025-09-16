package com.lis.astm.simulator.messages;

import java.util.Random;
import java.util.Arrays;
import java.util.List;

/**
 * Sample Data Generator
 * 
 * Generates realistic laboratory test data for:
 * - Complete Blood Count (CBC) panels
 * - Basic Chemistry panels
 * - Individual test results with proper units and reference ranges
 */
public class SampleDataGenerator {
    
    private static final Random random = new Random();
    
    // CBC Reference Ranges and Units
    private static final TestDefinition WBC = new TestDefinition("WBC", "10^3/uL", "4.5-11.0", 4.5, 11.0);
    private static final TestDefinition RBC = new TestDefinition("RBC", "10^6/uL", "4.20-5.40", 4.20, 5.40);
    private static final TestDefinition HGB = new TestDefinition("HGB", "g/dL", "12.0-16.0", 12.0, 16.0);
    private static final TestDefinition HCT = new TestDefinition("HCT", "%", "36.0-46.0", 36.0, 46.0);
    private static final TestDefinition PLT = new TestDefinition("PLT", "10^3/uL", "150-450", 150, 450);
    private static final TestDefinition MCH = new TestDefinition("MCH", "pg", "27-33", 27, 33);
    private static final TestDefinition MCHC = new TestDefinition("MCHC", "g/dL", "32-36", 32, 36);
    private static final TestDefinition MCV = new TestDefinition("MCV", "fL", "80-100", 80, 100);
    private static final TestDefinition RDW = new TestDefinition("RDW", "%", "11.5-14.5", 11.5, 14.5);
    private static final TestDefinition MPV = new TestDefinition("MPV", "fL", "7.0-11.0", 7.0, 11.0);
    
    // Chemistry Reference Ranges and Units
    private static final TestDefinition GLU = new TestDefinition("GLU", "mg/dL", "70-100", 70, 100);
    private static final TestDefinition BUN = new TestDefinition("BUN", "mg/dL", "7-20", 7, 20);
    private static final TestDefinition CREA = new TestDefinition("CREA", "mg/dL", "0.6-1.2", 0.6, 1.2);
    private static final TestDefinition NA = new TestDefinition("NA", "mmol/L", "136-145", 136, 145);
    private static final TestDefinition K = new TestDefinition("K", "mmol/L", "3.5-5.0", 3.5, 5.0);
    private static final TestDefinition CL = new TestDefinition("CL", "mmol/L", "98-107", 98, 107);
    private static final TestDefinition CO2 = new TestDefinition("CO2", "mmol/L", "22-28", 22, 28);
    private static final TestDefinition ALT = new TestDefinition("ALT", "U/L", "7-56", 7, 56);
    private static final TestDefinition AST = new TestDefinition("AST", "U/L", "10-40", 10, 40);
    private static final TestDefinition TBIL = new TestDefinition("TBIL", "mg/dL", "0.2-1.2", 0.2, 1.2);
    
    /**
     * Generate a complete CBC panel with all standard tests
     */
    public String generateCbcPanel() {
        return generateTestResults(Arrays.asList(WBC, RBC, HGB, HCT, PLT, MCH, MCHC, MCV, RDW, MPV));
    }
    
    /**
     * Generate a basic chemistry panel
     */
    public String generateChemistryPanel() {
        return generateTestResults(Arrays.asList(GLU, BUN, CREA, NA, K, CL, CO2, ALT, AST, TBIL));
    }
    
    /**
     * Generate a custom panel with specified test codes
     */
    public String generateCustomPanel(String... testCodes) {
        StringBuilder results = new StringBuilder();
        
        for (int i = 0; i < testCodes.length; i++) {
            if (i > 0) results.append(";");
            
            TestDefinition test = getTestDefinition(testCodes[i]);
            if (test != null) {
                results.append(generateTestResult(test));
            } else {
                // Generate a basic result for unknown test codes
                results.append(testCodes[i]).append(":").append(generateRandomValue(1, 100, 1));
            }
        }
        
        return results.toString();
    }
    
    /**
     * Generate abnormal results with flags for testing critical value handling
     */
    public String generateAbnormalCbcPanel() {
        StringBuilder results = new StringBuilder();
        
        // Generate some critical values
        results.append("WBC:").append(generateRandomValue(15.0, 25.0, 1)).append(":10^3/uL:4.5-11.0:H");
        results.append(";RBC:").append(generateRandomValue(3.0, 3.8, 2)).append(":10^6/uL:4.20-5.40:L");
        results.append(";HGB:").append(generateRandomValue(8.0, 10.0, 1)).append(":g/dL:12.0-16.0:L");
        results.append(";HCT:").append(generateRandomValue(25.0, 30.0, 1)).append(":%:36.0-46.0:L");
        results.append(";PLT:").append(generateRandomValue(50, 100, 0)).append(":10^3/uL:150-450:L");
        
        // Add normal values for other tests
        results.append(";").append(generateTestResult(MCH));
        results.append(";").append(generateTestResult(MCHC));
        results.append(";").append(generateTestResult(MCV));
        results.append(";").append(generateTestResult(RDW));
        results.append(";").append(generateTestResult(MPV));
        
        return results.toString();
    }
    
    /**
     * Generate abnormal chemistry results
     */
    public String generateAbnormalChemistryPanel() {
        StringBuilder results = new StringBuilder();
        
        // Generate some critical values
        results.append("GLU:").append(generateRandomValue(250, 400, 0)).append(":mg/dL:70-100:H");
        results.append(";BUN:").append(generateRandomValue(50, 80, 0)).append(":mg/dL:7-20:H");
        results.append(";CREA:").append(generateRandomValue(3.0, 5.0, 1)).append(":mg/dL:0.6-1.2:H");
        results.append(";K:").append(generateRandomValue(2.0, 2.8, 1)).append(":mmol/L:3.5-5.0:L");
        
        // Add normal values for other tests
        results.append(";").append(generateTestResult(NA));
        results.append(";").append(generateTestResult(CL));
        results.append(";").append(generateTestResult(CO2));
        results.append(";").append(generateTestResult(ALT));
        results.append(";").append(generateTestResult(AST));
        results.append(";").append(generateTestResult(TBIL));
        
        return results.toString();
    }
    
    /**
     * Generate realistic patient demographics
     */
    public PatientData generatePatientData() {
        String[] firstNames = {"John", "Jane", "Michael", "Sarah", "David", "Lisa", "Robert", "Emily", "James", "Ashley"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez"};
        
        String firstName = firstNames[random.nextInt(firstNames.length)];
        String lastName = lastNames[random.nextInt(lastNames.length)];
        String patientId = "P" + String.format("%06d", random.nextInt(999999));
        String accessionNumber = "A" + System.currentTimeMillis() % 1000000;
        
        return new PatientData(patientId, firstName, lastName, accessionNumber);
    }
    
    /**
     * Generate test results for a list of test definitions
     */
    private String generateTestResults(List<TestDefinition> tests) {
        StringBuilder results = new StringBuilder();
        
        for (int i = 0; i < tests.size(); i++) {
            if (i > 0) results.append(";");
            results.append(generateTestResult(tests.get(i)));
        }
        
        return results.toString();
    }
    
    /**
     * Generate a single test result with appropriate format
     */
    private String generateTestResult(TestDefinition test) {
        double value = generateRandomValue(test.minValue, test.maxValue, getDecimalPlaces(test.testCode));
        return test.testCode + ":" + formatValue(value, test.testCode) + ":" + 
               test.units + ":" + test.referenceRange;
    }
    
    /**
     * Generate a random value within the specified range
     */
    private double generateRandomValue(double min, double max, int decimalPlaces) {
        double value = min + (max - min) * random.nextDouble();
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale) / scale;
    }
    
    /**
     * Format value with appropriate decimal places for the test
     */
    private String formatValue(double value, String testCode) {
        int decimalPlaces = getDecimalPlaces(testCode);
        
        if (decimalPlaces == 0) {
            return String.valueOf((int) value);
        } else {
            return String.format("%." + decimalPlaces + "f", value);
        }
    }
    
    /**
     * Get appropriate decimal places for each test type
     */
    private int getDecimalPlaces(String testCode) {
        switch (testCode.toUpperCase()) {
            case "PLT":
            case "WBC":
            case "GLU":
            case "BUN":
            case "NA":
            case "CL":
            case "CO2":
            case "ALT":
            case "AST":
                return 0;
            case "K":
            case "CREA":
            case "TBIL":
            case "HGB":
            case "HCT":
            case "RDW":
            case "MPV":
                return 1;
            case "RBC":
                return 2;
            default:
                return 1;
        }
    }
    
    /**
     * Get test definition by test code
     */
    private TestDefinition getTestDefinition(String testCode) {
        switch (testCode.toUpperCase()) {
            case "WBC": return WBC;
            case "RBC": return RBC;
            case "HGB": return HGB;
            case "HCT": return HCT;
            case "PLT": return PLT;
            case "MCH": return MCH;
            case "MCHC": return MCHC;
            case "MCV": return MCV;
            case "RDW": return RDW;
            case "MPV": return MPV;
            case "GLU": return GLU;
            case "BUN": return BUN;
            case "CREA": return CREA;
            case "NA": return NA;
            case "K": return K;
            case "CL": return CL;
            case "CO2": return CO2;
            case "ALT": return ALT;
            case "AST": return AST;
            case "TBIL": return TBIL;
            default: return null;
        }
    }
    
    /**
     * Test definition with reference ranges
     */
    private static class TestDefinition {
        final String testCode;
        final String units;
        final String referenceRange;
        final double minValue;
        final double maxValue;
        
        TestDefinition(String testCode, String units, String referenceRange, double minValue, double maxValue) {
            this.testCode = testCode;
            this.units = units;
            this.referenceRange = referenceRange;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
    }
    
    /**
     * Patient demographic data
     */
    public static class PatientData {
        private final String patientId;
        private final String firstName;
        private final String lastName;
        private final String accessionNumber;
        
        public PatientData(String patientId, String firstName, String lastName, String accessionNumber) {
            this.patientId = patientId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.accessionNumber = accessionNumber;
        }
        
        public String getPatientId() { return patientId; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getFullName() { return firstName + " " + lastName; }
        public String getAccessionNumber() { return accessionNumber; }
        
        @Override
        public String toString() {
            return String.format("Patient: %s (%s), Accession: %s", getFullName(), patientId, accessionNumber);
        }
    }
}
