package com.lis.astm.swing;

import com.lis.astm.model.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class AstmParserSwingApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(AstmParserSwingApp::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("ASTM Parser - Field-by-Field View");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea inputArea = new JTextArea(6, 80);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane inputScroll = new JScrollPane(inputArea);
        JButton parseButton = new JButton("Parse ASTM Message");
        parseButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        JTabbedPane tabbedPane = new JTabbedPane();

        parseButton.addActionListener((ActionEvent e) -> {
            tabbedPane.removeAll();
            String raw = inputArea.getText();
            if (raw == null || raw.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please paste a raw ASTM message.", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                AstmMessage msg = parseAstmMessage(raw);
                addTabsForRecords(tabbedPane, msg);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Parse error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(new JLabel("Paste raw ASTM message below and click Parse:"), BorderLayout.NORTH);
        topPanel.add(inputScroll, BorderLayout.CENTER);
        topPanel.add(parseButton, BorderLayout.EAST);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    private static AstmMessage parseAstmMessage(String raw) throws Exception {
        AstmMessage msg = new AstmMessage();
        ResultRecord currentResultRecord = null; //
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split("\\|", -1);
            String type = fields[0].toUpperCase();
            switch (type) {
                case "H": 
                    HeaderRecord header = new HeaderRecord();
                    parseHeaderRecord(header, fields);
                    msg.setHeaderRecord(header); 
                    break;
                case "P": 
                    PatientRecord patient = new PatientRecord();
                    parsePatientRecord(patient, fields);
                    msg.setPatientRecord(patient); 
                    break;
                case "O": 
                    OrderRecord order = new OrderRecord();
                    parseOrderRecord(order, fields);
                    msg.addOrderRecord(order); 
                    break;
                // case "R": 
                //     ResultRecord result = new ResultRecord();
                //     parseResultRecord(result, fields);
                //     msg.addResultRecord(result); 
                //     break;
                case "R":
                        // A new Result record is found
                        ResultRecord result = new ResultRecord();
                        parseResultRecord(result, fields);
                        msg.addResultRecord(result);
                        currentResultRecord = result; // Set this as the current context for M records
                        break;    
                case "Q": 
                    QueryRecord query = new QueryRecord();
                    parseQueryRecord(query, fields);
                    msg.addQueryRecord(query); 
                    break;
                case "M": 
                    // MResultRecord mResult = new MResultRecord();
                    // parseMResultRecord(mResult, fields);
                    // msg..addMResultRecord(mResult); 
                    // break;
                    MResultRecord mResult = new MResultRecord();
                    if (currentResultRecord != null) {
                            parseMResultRecord(mResult, fields);
                            currentResultRecord.addMResultRecord(mResult);
                        } 
                        break;
                case "L": 
                    TerminatorRecord terminator = new TerminatorRecord();
                    parseTerminatorRecord(terminator, fields);
                    msg.setTerminatorRecord(terminator); 
                    break;
            }
        }
        return msg;
    }
    
    private static void parseHeaderRecord(HeaderRecord record, String[] fields) {
        if (fields.length > 1) record.setDelimiters(getFieldValue(fields, 1));
        if (fields.length > 2) record.setMessageControlId(getFieldValue(fields, 2));
        if (fields.length > 3) record.setAccessPassword(getFieldValue(fields, 3));
        if (fields.length > 4) record.setSenderName(getFieldValue(fields, 4));
        if (fields.length > 5) record.setSenderAddress(getFieldValue(fields, 5));
        if (fields.length > 6) record.setReservedField(getFieldValue(fields, 6));
        if (fields.length > 7) record.setSenderPhone(getFieldValue(fields, 7));
        if (fields.length > 8) record.setCharacteristics(getFieldValue(fields, 8));
        if (fields.length > 9) record.setReceiverName(getFieldValue(fields, 9));
        if (fields.length > 10) record.setComments(getFieldValue(fields, 10));
        if (fields.length > 11) record.setProcessingId(getFieldValue(fields, 11));
        if (fields.length > 12) record.setVersionNumber(getFieldValue(fields, 12));
    }
    
    private static void parsePatientRecord(PatientRecord record, String[] fields) {
        if (fields.length > 1) record.setSequenceNumber(parseInteger(getFieldValue(fields, 1)));
        if (fields.length > 2) record.setPracticeAssignedPatientId(getFieldValue(fields, 2));
        if (fields.length > 3) record.setLaboratoryAssignedPatientId(getFieldValue(fields, 3));
        if (fields.length > 4) record.setPatientIdAlternate(getFieldValue(fields, 4));
        if (fields.length > 5) record.setPatientName(getFieldValue(fields, 5));
        if (fields.length > 6) record.setMothersMaidenName(getFieldValue(fields, 6));
        if (fields.length > 7) record.setBirthDate(parseDateTime(getFieldValue(fields, 7)));
        if (fields.length > 8) record.setPatientSex(getFieldValue(fields, 8));
        if (fields.length > 13) record.setAttendingPhysicianId(getFieldValue(fields, 13));
    }
    
    private static void parseOrderRecord(OrderRecord record, String[] fields) {
        if (fields.length > 1) record.setSequenceNumber(parseInteger(getFieldValue(fields, 1)));
        if (fields.length > 2) record.setSpecimenId(getFieldValue(fields, 2));
        if (fields.length > 3) record.setUniversalTestId(getFieldValue(fields, 3));
        if (fields.length > 4) record.setPriority(getFieldValue(fields, 4));
        if (fields.length > 11) record.setActionCode(getFieldValue(fields, 11));
        if (fields.length > 15) record.setInstrumentSpecimenId(getFieldValue(fields, 15));
    }
    
    private static void parseResultRecord(ResultRecord record, String[] fields) {
        if (fields.length > 1) record.setSequenceNumber(parseInteger(getFieldValue(fields, 1)));
        if (fields.length > 2) record.setUniversalTestId(getFieldValue(fields, 2));
        if (fields.length > 3) record.setDataValue(getFieldValue(fields, 3));
        if (fields.length > 4) record.setUnits(getFieldValue(fields, 4));
        if (fields.length > 5) record.setReferenceRanges(getFieldValue(fields, 5));
        if (fields.length > 6) record.setResultAbnormalFlags(getFieldValue(fields, 6));
        if (fields.length > 8) record.setResultStatus(getFieldValue(fields, 8));
        if (fields.length > 10) record.setOperatorId(getFieldValue(fields, 10));
        if (fields.length > 13) record.setInstrumentId(getFieldValue(fields, 13));
    }
    
    private static void parseQueryRecord(QueryRecord record, String[] fields) {
        if (fields.length > 1) record.setSequenceNumber(parseInteger(getFieldValue(fields, 1)));
        if (fields.length > 2) record.setStartingRangeId(getFieldValue(fields, 2));
        if (fields.length > 3) record.setEndingRangeId(getFieldValue(fields, 3));
        if (fields.length > 4) record.setUniversalTestId(getFieldValue(fields, 4));
        if (fields.length > 5) record.setNatureOfRequestTimeLimits(getFieldValue(fields, 5));
        if (fields.length > 6) record.setBeginningRequestResultsDateTime(getFieldValue(fields, 6));
        if (fields.length > 7) record.setEndingRequestResultsDateTime(getFieldValue(fields, 7));
        if (fields.length > 12) record.setRequestInformationStatusCodes(getFieldValue(fields, 12));
    }
    
    private static void parseMResultRecord(MResultRecord record, String[] fields) {
        if (fields.length > 1) record.setSequenceNumber(parseInteger(getFieldValue(fields, 1)));
        if (fields.length > 2) record.setResultWellName(getFieldValue(fields, 2));
        if (fields.length > 3) record.setTypeOfCard(getFieldValue(fields, 3));
        if (fields.length > 5) record.setResultInformation(getFieldValue(fields, 5));
        if (fields.length > 6) record.setTestName(getFieldValue(fields, 6));
    }
    
    private static void parseTerminatorRecord(TerminatorRecord record, String[] fields) {
        if (fields.length > 1) record.setSequenceNumber(parseInteger(getFieldValue(fields, 1)));
        if (fields.length > 2) record.setTerminationCode(getFieldValue(fields, 2));
    }
    
    private static String getFieldValue(String[] fields, int index) {
        return (index < fields.length && !fields[index].trim().isEmpty()) ? fields[index].trim() : null;
    }
    
    private static Integer parseInteger(String value) {
        try {
            return (value != null && !value.isEmpty()) ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            String dateStr = value.trim();
            
            // Handle different ASTM date formats: YYYYMMDD, YYYYMMDDHHMM, or YYYYMMDDHHMMSS
            if (dateStr.length() == 8) {
                // YYYYMMDD format - append default time
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
            } else if (dateStr.length() == 12) {
                // YYYYMMDDHHMM format
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
            } else if (dateStr.length() == 14) {
                // YYYYMMDDHHMMSS format
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            } else {
                // Default to date format and start of day
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void addTabsForRecords(JTabbedPane tabs, AstmMessage msg) {
        if (msg.getHeaderRecord() != null) {
            tabs.addTab("Header (H)", createRecordPanel(msg.getHeaderRecord(), "Header Record"));
        }
        if (msg.getPatientRecord() != null) {
            tabs.addTab("Patient (P)", createRecordPanel(msg.getPatientRecord(), "Patient Record"));
        }
        addMultiRecordTab(tabs, "Order (O)", msg.getOrderRecords(), "Order Record");
        addMultiRecordTab(tabs, "Result (R)", msg.getResultRecords(), "Result Record");
        addMultiRecordTab(tabs, "Query (Q)", msg.getQueryRecords(), "Query Record");
        for(ResultRecord result: msg.getResultRecords())
            addMultiRecordTab(tabs, "M-Result (M)", result.getMResultRecords(), "M-Result Record");
        if (msg.getTerminatorRecord() != null) {
            tabs.addTab("Terminator (L)", createRecordPanel(msg.getTerminatorRecord(), "Terminator Record"));
        }
    }

    private static <T> void addMultiRecordTab(JTabbedPane tabs, String tabTitle, List<T> records, String recordType) {
        if (records != null && !records.isEmpty()) {
            if (records.size() == 1) {
                // Single record - show directly
                tabs.addTab(tabTitle, createRecordPanel(records.get(0), recordType + " 1"));
            } else {
                // Multiple records - create selector
                JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
                mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                // Record selector
                JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                selectorPanel.add(new JLabel("Select " + recordType + ":"));
                JComboBox<String> recordSelector = new JComboBox<>();
                for (int i = 0; i < records.size(); i++) {
                    recordSelector.addItem(recordType + " " + (i + 1));
                }
                selectorPanel.add(recordSelector);
                
                // Record display panel
                JPanel displayPanel = new JPanel(new CardLayout());
                for (int i = 0; i < records.size(); i++) {
                    JScrollPane scrollPane = new JScrollPane(createRecordTablePanel(records.get(i), recordType + " " + (i + 1)));
                    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    displayPanel.add(scrollPane, recordType + " " + (i + 1));
                }
                
                recordSelector.addActionListener(e -> {
                    CardLayout layout = (CardLayout) displayPanel.getLayout();
                    layout.show(displayPanel, (String) recordSelector.getSelectedItem());
                });
                
                mainPanel.add(selectorPanel, BorderLayout.NORTH);
                mainPanel.add(displayPanel, BorderLayout.CENTER);
                tabs.addTab(tabTitle, mainPanel);
            }
        }
    }

    private static JComponent createRecordPanel(Object record, String title) {
        JScrollPane scrollPane = new JScrollPane(createRecordTablePanel(record, title));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    private static JPanel createRecordTablePanel(Object record, String title) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Create table
        JTable table = createFieldTable(record);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(240, 240, 240));
        table.setGridColor(Color.LIGHT_GRAY);
        table.setShowGrid(true);
        
        // Enable multi-line cell display
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        
        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(80);  // Field Number
        table.getColumnModel().getColumn(1).setPreferredWidth(250); // Field Name
        table.getColumnModel().getColumn(2).setPreferredWidth(400); // Field Value (wider for complex fields)
        table.getColumnModel().getColumn(3).setPreferredWidth(120); // Data Type
        
        // Make table non-editable but selectable
        table.setDefaultEditor(Object.class, null);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane tableScrollPane = new JScrollPane(table);
        panel.add(tableScrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private static JTable createFieldTable(Object record) {
        String[] columnNames = {"Field #", "Field Name", "Value", "Type"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        
        String recordType = record.getClass().getSimpleName();
        
        try {
            // Get all protocol fields for this record type
            String[][] protocolFields = getProtocolFieldsForRecord(recordType);
            
            // Create a map of actual field values
            Field[] actualFields = record.getClass().getDeclaredFields();
            java.util.Map<String, Object> fieldValues = new java.util.HashMap<>();
            java.util.Map<String, Class<?>> fieldTypes = new java.util.HashMap<>();
            
            for (Field field : actualFields) {
                field.setAccessible(true);
                Object value = field.get(record);
                fieldValues.put(field.getName(), value);
                fieldTypes.put(field.getName(), field.getType());
            }
            
            // Add all protocol fields in order
            for (String[] fieldInfo : protocolFields) {
                String fieldNumber = fieldInfo[0];
                String protocolFieldName = fieldInfo[1];
                String javaFieldName = fieldInfo[2];
                String protocolDataType = fieldInfo[3];
                
                Object fieldValue = fieldValues.get(javaFieldName);
                String displayValue = formatFieldValue(fieldValue, fieldNumber, javaFieldName);
                
                // Use actual Java type if available, otherwise show protocol type
                String dataType = protocolDataType;
                if (fieldTypes.containsKey(javaFieldName)) {
                    Class<?> actualType = fieldTypes.get(javaFieldName);
                    if (actualType == String.class) dataType = "String";
                    else if (actualType == Integer.class || actualType == int.class) dataType = "Integer";
                    else if (actualType == LocalDateTime.class) dataType = "DateTime";
                    else if (actualType == List.class) dataType = "List";
                    else dataType = actualType.getSimpleName();
                }
                
                model.addRow(new Object[]{
                    fieldNumber,
                    protocolFieldName,
                    displayValue,
                    dataType
                });
            }
        } catch (Exception e) {
            model.addRow(new Object[]{"Error", "Error displaying fields", e.getMessage(), "Error"});
        }
        
        JTable table = new JTable(model);
        
        // Custom cell renderer for better visibility and multi-line support
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                // For multi-line content in Value column, use JTextArea
                if (column == 2 && value != null && value.toString().contains("\n")) {
                    JTextArea textArea = new JTextArea(value.toString());
                    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                    textArea.setLineWrap(false);
                    textArea.setWrapStyleWord(false);
                    textArea.setOpaque(true);
                    textArea.setEditable(false);
                    
                    // Color coding for multi-line fields
                    if (value.toString().equals("(empty)")) {
                        textArea.setForeground(Color.GRAY);
                    } else {
                        textArea.setForeground(Color.BLACK);
                    }
                    
                    // Set background color
                    if (isSelected) {
                        textArea.setBackground(table.getSelectionBackground());
                        textArea.setForeground(table.getSelectionForeground());
                    } else {
                        if (row % 2 == 0) {
                            textArea.setBackground(Color.WHITE);
                        } else {
                            textArea.setBackground(new Color(248, 248, 248));
                        }
                    }
                    
                    // Adjust row height based on content
                    int lines = value.toString().split("\n").length;
                    int preferredHeight = Math.max(25, lines * 16 + 10);
                    if (table.getRowHeight(row) < preferredHeight) {
                        table.setRowHeight(row, preferredHeight);
                    }
                    
                    return textArea;
                }
                
                // Regular single-line rendering
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                // Color coding for single-line fields
                if (column == 2 && (value == null || value.toString().equals("(empty)"))) {
                    c.setForeground(Color.GRAY);
                } else if (column == 0) {
                    c.setForeground(new Color(0, 100, 200)); // Blue for field numbers
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (column == 2 && value != null && (value.toString().contains("^") || value.toString().contains("&"))) {
                    c.setForeground(new Color(0, 128, 0)); // Green for complex fields
                } else {
                    c.setForeground(Color.BLACK);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                
                // Alternating row colors for single-line content
                if (!isSelected) {
                    if (row % 2 == 0) {
                        c.setBackground(Color.WHITE);
                    } else {
                        c.setBackground(new Color(248, 248, 248));
                    }
                }
                
                return c;
            }
        });
        
        return table;
    }
    
    private static String[][] getProtocolFieldsForRecord(String recordType) {
        // Return array format: [fieldNumber, protocolFieldName, javaFieldName, dataType]
        switch (recordType) {
            case "HeaderRecord":
                return new String[][] {
                    {"H.1", "Record Type ID", "recordType", "String"},
                    {"H.2", "Delimiter Definition", "delimiters", "String"},
                    {"H.3", "Message Control ID", "messageControlId", "String"},
                    {"H.4", "Access Password", "accessPassword", "String"},
                    {"H.5", "Sender Name or ID", "senderName", "String"},
                    {"H.6", "Sender Street Address", "senderAddress", "String"},
                    {"H.7", "Reserved Field", "reservedField", "String"},
                    {"H.8", "Sender Telephone Number", "senderPhone", "String"},
                    {"H.9", "Characteristics of Sender", "characteristics", "String"},
                    {"H.10", "Receiver ID", "receiverName", "String"},
                    {"H.11", "Comment or Special Instructions", "comments", "String"},
                    {"H.12", "Processing ID", "processingId", "String"},
                    {"H.13", "Version Number", "versionNumber", "String"},
                    {"H.14", "Date and Time of Message", "dateTime", "DateTime"}
                };
                
            case "PatientRecord":
                return new String[][] {
                    {"P.1", "Record Type ID", "recordType", "String"},
                    {"P.2", "Sequence Number", "sequenceNumber", "Integer"},
                    {"P.3", "Practice Assigned Patient ID", "practiceAssignedPatientId", "String"},
                    {"P.4", "Laboratory Assigned Patient ID", "laboratoryAssignedPatientId", "String"},
                    {"P.5", "Patient ID No. 3", "patientIdAlternate", "String"},
                    {"P.6", "Patient Name", "patientName", "String"},
                    {"P.7", "Mother's Maiden Name", "mothersMaidenName", "String"},
                    {"P.8", "Birth Date", "birthDate", "DateTime"},
                    {"P.9", "Patient Sex", "patientSex", "String"},
                    {"P.10", "Patient Race-Ethnic Origin", "patientRaceEthnic", "String"},
                    {"P.11", "Patient Address", "patientAddress", "String"},
                    {"P.12", "Reserved Field", "reserved", "String"},
                    {"P.13", "Patient Telephone Number", "patientTelephoneNumber", "String"},
                    {"P.14", "Attending Physician ID", "attendingPhysicianId", "String"},
                    {"P.15", "Special Field 1", "specialField1", "String"},
                    {"P.16", "Special Field 2", "specialField2", "String"},
                    {"P.17", "Patient Height", "patientHeight", "String"},
                    {"P.18", "Patient Weight", "patientWeight", "String"},
                    {"P.19", "Patient's Known or Suspected Diagnosis", "patientDiagnosis", "String"},
                    {"P.20", "Patient Active Medications", "patientActiveMediation", "String"},
                    {"P.21", "Patient's Diet", "patientDiet", "String"},
                    {"P.22", "Practice Field No. 1", "practiceField1", "String"},
                    {"P.23", "Practice Field No. 2", "practiceField2", "String"},
                    {"P.24", "Admission and Discharge Dates", "admissionDate", "DateTime"},
                    {"P.25", "Admission Status", "admissionStatus", "String"},
                    {"P.26", "Location", "location", "String"},
                    {"P.27", "Nature of Alt. Diagnostic Code and Class", "altDiagnosticCodeNature", "String"},
                    {"P.28", "Alternative Diagnostic Code and Classification", "altDiagnosticCode", "String"},
                    {"P.29", "Patient Religion", "religion", "String"},
                    {"P.30", "Marital Status", "maritalStatus", "String"},
                    {"P.31", "Isolation Status", "isolationStatus", "String"},
                    {"P.32", "Language", "language", "String"},
                    {"P.33", "Hospital Service", "hospitalService", "String"},
                    {"P.34", "Hospital Institution", "hospitalInstitution", "String"},
                    {"P.35", "Dosage Category", "dosageCategory", "String"}
                };
                
            case "OrderRecord":
                return new String[][] {
                    {"O.1", "Record Type ID", "recordType", "String"},
                    {"O.2", "Sequence Number", "sequenceNumber", "Integer"},
                    {"O.3", "Specimen ID", "specimenId", "String"},
                    {"O.4", "Universal Test ID", "universalTestId", "String"},
                    {"O.5", "Priority", "priority", "String"},
                    {"O.6", "Requested/Ordered Date and Time", "requestedDateTime", "DateTime"},
                    {"O.7", "Specimen Collection Date and Time", "specimenCollectionDateTime", "DateTime"},
                    {"O.8", "Collector ID", "collectorId", "String"},
                    {"O.9", "Action Code", "actionCode", "String"},
                    {"O.10", "Danger Code", "dangerCode", "String"},
                    {"O.11", "Relevant Clinical Information", "relevantClinicalInfo", "String"},
                    {"O.12", "Date/Time Specimen Received", "dateTimeSpecimenReceived", "DateTime"},
                    {"O.13", "Specimen Descriptor", "specimenDescriptor", "String"},
                    {"O.14", "Ordering Physician", "orderingPhysician", "String"},
                    {"O.15", "Physician's Telephone Number", "physicianTelephoneNumber", "String"},
                    {"O.16", "User Field No. 1", "userField1", "String"},
                    {"O.17", "User Field No. 2", "userField2", "String"},
                    {"O.18", "Laboratory Field No. 1", "laboratoryField1", "String"},
                    {"O.19", "Laboratory Field No. 2", "laboratoryField2", "String"},
                    {"O.20", "Date and Time of Report", "dateTimeOfSpecimenReceipt", "DateTime"},
                    {"O.21", "Instrument Specimen ID", "instrumentSpecimenId", "String"}
                };
                
            case "ResultRecord":
                return new String[][] {
                    {"R.1", "Record Type ID", "recordType", "String"},
                    {"R.2", "Sequence Number", "sequenceNumber", "Integer"},
                    {"R.3", "Universal Test ID", "universalTestId", "String"},
                    {"R.4", "Data or Measurement Value", "dataValue", "String"},
                    {"R.5", "Units", "units", "String"},
                    {"R.6", "Reference Ranges", "referenceRanges", "String"},
                    {"R.7", "Result Abnormal Flags", "resultAbnormalFlags", "String"},
                    {"R.8", "Nature of Abnormality Testing", "natureOfAbnormalityTesting", "String"},
                    {"R.9", "Result Status", "resultStatus", "String"},
                    {"R.10", "Date of Change in Instrument Normative Values or Units", "dateOfChangeInInstrumentNormativeValues", "DateTime"},
                    {"R.11", "Operator Identification", "operatorId", "String"},
                    {"R.12", "Date/Time Test Started", "dateTimeTestStarted", "DateTime"},
                    {"R.13", "Date/Time Test Completed", "dateTimeTestCompleted", "DateTime"},
                    {"R.14", "Instrument Identification", "instrumentId", "String"},
                    {"R.15", "Test Name", "testName", "String"}
                };
                
            case "QueryRecord":
                return new String[][] {
                    {"Q.1", "Record Type ID", "recordType", "String"},
                    {"Q.2", "Sequence Number", "sequenceNumber", "Integer"},
                    {"Q.3", "Starting Range ID Number", "startingRangeId", "String"},
                    {"Q.4", "Ending Range ID Number", "endingRangeId", "String"},
                    {"Q.5", "Universal Test ID", "universalTestId", "String"},
                    {"Q.6", "Nature of Request Time Limits", "natureOfRequestTimeLimits", "String"},
                    {"Q.7", "Beginning Request Results Date and Time", "beginningRequestResultsDateTime", "String"},
                    {"Q.8", "Ending Request Results Date and Time", "endingRequestResultsDateTime", "String"},
                    {"Q.9", "Requesting Physician Name", "requestingPhysicianName", "String"},
                    {"Q.10", "Requesting Physician's Telephone Number", "requestingPhysicianTelephoneNumber", "String"},
                    {"Q.11", "User Field No. 1", "userField1", "String"},
                    {"Q.12", "User Field No. 2", "userField2", "String"},
                    {"Q.13", "Request Information Status Codes", "requestInformationStatusCodes", "String"}
                };
                
            case "MResultRecord":
                return new String[][] {
                    {"M.1", "Record Type ID", "recordType", "String"},
                    {"M.2", "Sequence Number", "sequenceNumber", "Integer"},
                    {"M.3", "Result Well Name", "resultWellName", "String"},
                    {"M.4", "Type of Card", "typeOfCard", "String"},
                    {"M.5", "Reagent Information", "reagentInformation", "List"},
                    {"M.6", "Result Information", "resultInformation", "String"},
                    {"M.7", "Test Name", "testName", "String"}
                };
                
            case "TerminatorRecord":
                return new String[][] {
                    {"L.1", "Record Type ID", "recordType", "String"},
                    {"L.2", "Sequence Number", "sequenceNumber", "Integer"},
                    {"L.3", "Termination Code", "terminationCode", "String"}
                };
                
            default:
                return new String[][] {
                    {"?.1", "Unknown Record Type", "recordType", "String"}
                };
        }
    }
    
    private static String formatFieldValue(Object value, String fieldNumber, String javaFieldName) {
        if (value == null) {
            return "(empty)";
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        String stringValue = value.toString();
        if (stringValue.isEmpty()) {
            return "(empty)";
        }
        
        // Check if the field contains ASTM sub-components or repeating fields
        if (stringValue.contains("^") || stringValue.contains("&")) {
            return formatComplexFieldWithNames(stringValue, fieldNumber, javaFieldName);
        }
        
        return stringValue;
    }
    
    private static String formatComplexFieldWithNames(String value, String fieldNumber, String javaFieldName) {
        StringBuilder formatted = new StringBuilder();
        
        // First show the raw value
        formatted.append(value).append("\n");
        
        // Handle repeating fields separated by &
        if (value.contains("&")) {
            String[] repeatingFields = value.split("&", -1);
            for (int i = 0; i < repeatingFields.length; i++) {
                String field = repeatingFields[i].trim();
                if (field.isEmpty()) continue;
                
                // Check if this repeating field has sub-components
                if (field.contains("^")) {
                    formatted.append("├─ Repeat ").append(i + 1).append(":\n");
                    String[] subComponents = field.split("\\^", -1);
                    String[] componentNames = getComponentNames(fieldNumber, javaFieldName);
                    
                    for (int j = 0; j < subComponents.length; j++) {
                        String component = subComponents[j].trim();
                        if (!component.isEmpty() || j < componentNames.length) {
                            String prefix = (j == subComponents.length - 1) ? "│  └─ " : "│  ├─ ";
                            String componentName = (j < componentNames.length) ? componentNames[j] : ("Component " + (j + 1));
                            String displayComponent = component.isEmpty() ? "(empty)" : component;
                            formatted.append(prefix).append(componentName)
                                     .append(": ").append(displayComponent).append("\n");
                        }
                    }
                } else {
                    String prefix = (i == repeatingFields.length - 1) ? "└─ " : "├─ ";
                    formatted.append(prefix).append("Repeat ").append(i + 1)
                             .append(": ").append(field).append("\n");
                }
            }
        } 
        // Handle sub-components separated by ^ (without repeating fields)
        else if (value.contains("^")) {
            String[] subComponents = value.split("\\^", -1);
            String[] componentNames = getComponentNames(fieldNumber, javaFieldName);
            
            for (int i = 0; i < subComponents.length; i++) {
                String component = subComponents[i].trim();
                if (!component.isEmpty() || i < componentNames.length) {
                    String prefix = (i == subComponents.length - 1) ? "└─ " : "├─ ";
                    String componentName = (i < componentNames.length) ? componentNames[i] : ("Component " + (i + 1));
                    String displayComponent = component.isEmpty() ? "(empty)" : component;
                    formatted.append(prefix).append(componentName)
                             .append(": ").append(displayComponent).append("\n");
                }
            }
        }
        
        // Remove trailing newline
        if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) == '\n') {
            formatted.setLength(formatted.length() - 1);
        }
        
        return formatted.toString();
    }
    
    private static String[] getComponentNames(String fieldNumber, String javaFieldName) {
        // Return component names based on ORTHO VISION® ASTM E1394 specification
        switch (fieldNumber) {
            // Header Record Components
            case "H.2": // Field Delimiters: "|\^&"
                return new String[]{"Field Delimiter (|)", "Repeat Delimiter (\\)", "Component Delimiter (^)", "Escape Delimiter (&)"};
            case "H.5": // Sender Name/ID
                return new String[]{"Manufacturer Name (OCD)", "Product Name (VISION)", "Software Version", "Instrument ID"};
                
            // Patient Record Components
            case "P.5": // Additional Patient IDs
                return new String[]{"National ID", "Medical Record", "Other ID"};
            case "P.6": // Patient Name
                return new String[]{"Last Name", "First Name", "Middle Initial", "Suffix", "Title"};
            case "P.14": // Attending Physician (single physician allowed)
                return new String[]{"Physician ID", "Last Name", "First Name", "Middle Initial"};
                
            // Order Record Components  
            case "O.5": // Universal Test ID (complex field with multiple subcomponents)
                return new String[]{"Profile Name", "Number of Donor Samples", "1st Donor Specimen ID", "Sample Type of 1st Donor", 
                                  "Number of Card Lots", "1st Card ID", "1st Card Lot ID", "Number of Reagent Lots", 
                                  "1st Reagent ID", "1st Reagent Lot ID"};
            case "O.14": // Relevant Clinical Info - Expected Test Results
                return new String[]{"Test Name", "Expected Result"};
                
            // Query Record Components
            case "Q.3": // Starting Range ID Number
                return new String[]{"Computer System Patient ID", "Computer System Sample ID"};
                
            // Result Record Components
            case "R.3": // Test ID
                return new String[]{"Analysis", "Donor Specimen ID"};
            case "R.11": // Operator Identification (conditional based on Include Operator setting)
                return new String[]{"Instrument Operator", "Verifier"};
                
            // M-Result Record Components
            case "M.4": // Card Information
                return new String[]{"Type of Card", "Number of the Well (1-6)", "Card ID Number", "Card Lot Number", 
                                  "Card Expiration Date", "Mono Image File Name", "Color Image File Name"};
            case "M.5": // Reagent Information (repeating group)
                return new String[]{"Reagent Name", "Reagent Lot Number", "Reagent Expiration Date"};
            case "M.6": // Result Details
                return new String[]{"Final Result or Error", "Manual Correction Flag (M/A)", "Read Result or Error", "Operator ID"};
                
            // Default for unknown fields
            default:
                return new String[]{"Component 1", "Component 2", "Component 3", "Component 4", "Component 5"};
        }
    }
}
