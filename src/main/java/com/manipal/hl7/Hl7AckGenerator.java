package com.manipal.hl7;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to parse incoming HL7 messages and generate
 * compliant HL7 ACK (Acknowledgment) messages.
 */
public class Hl7AckGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Parses the incoming HL7 message and generates a standard ACK.
     * Swaps sending/receiving application and facility, and matches the message control ID and version.
     *
     * @param rawHl7 The raw HL7 message string
     * @return The standard HL7 ACK message string (using \r as segment separator)
     */
    public static String generateAck(String rawHl7) {
        if (rawHl7 == null || rawHl7.trim().isEmpty()) {
            return generateGenericAck("UNKNOWN_ID", "AE", "Empty HL7 message received");
        }

        try {
            // HL7 segments can be separated by \r, \n, or \r\n
            String[] segments = rawHl7.split("[\\r\\n]+");
            String mshSegment = null;
            for (String segment : segments) {
                if (segment.startsWith("MSH")) {
                    mshSegment = segment;
                    break;
                }
            }

            if (mshSegment == null) {
                return generateGenericAck("UNKNOWN_ID", "AE", "MSH segment not found");
            }

            // The field separator is the 4th character of MSH (index 3)
            char fieldSepChar = mshSegment.charAt(3);
            String fieldSep = String.valueOf(fieldSepChar);
            
            // Split fields. Escape the field separator if it's a regex special char (like '|')
            String escapedSeparator = (fieldSepChar == '|' || fieldSepChar == '^' || fieldSepChar == '$' || fieldSepChar == '\\') 
                ? "\\" + fieldSepChar 
                : fieldSep;
            
            String[] fields = mshSegment.split(escapedSeparator, -1);
            
            // Extract necessary fields from the MSH segment
            String sendingApp = fields.length > 2 ? fields[2] : "";
            String sendingFacility = fields.length > 3 ? fields[3] : "";
            String receivingApp = fields.length > 4 ? fields[4] : "";
            String receivingFacility = fields.length > 5 ? fields[5] : "";
            String messageType = fields.length > 8 ? fields[8] : "ACK";
            String messageControlId = fields.length > 9 ? fields[9] : "UNKNOWN_ID";
            String processingId = fields.length > 10 ? fields[10] : "P";
            String versionId = fields.length > 11 ? fields[11] : "2.3.1";

            // Extract the trigger event (e.g., "S12" from "SIU^S12")
            String triggerEvent = "";
            if (messageType.contains("^")) {
                String[] parts = messageType.split("\\^");
                if (parts.length > 1) {
                    triggerEvent = parts[1];
                }
            } else if (messageType.contains("~")) {
                String[] parts = messageType.split("~");
                if (parts.length > 1) {
                    triggerEvent = parts[1];
                }
            }

            String ackMessageType = triggerEvent.isEmpty() ? "ACK" : "ACK^" + triggerEvent;
            String currentDateTime = LocalDateTime.now().format(DATE_FORMATTER);

            // Construct MSH segment of ACK (swap sender and receiver)
            StringBuilder ack = new StringBuilder();
            ack.append("MSH").append(fieldSepChar)
               .append("^~\\&").append(fieldSepChar)
               .append(receivingApp).append(fieldSepChar)        // ACK Sending App = Original Receiving App
               .append(receivingFacility).append(fieldSepChar)   // ACK Sending Facility = Original Receiving Facility
               .append(sendingApp).append(fieldSepChar)          // ACK Receiving App = Original Sending App
               .append(sendingFacility).append(fieldSepChar)     // ACK Receiving Facility = Original Sending Facility
               .append(currentDateTime).append(fieldSepChar)
               .append(fieldSepChar)                             // Security (empty)
               .append(ackMessageType).append(fieldSepChar)
               .append(messageControlId).append(fieldSepChar)
               .append(processingId).append(fieldSepChar)
               .append(versionId)
               .append("\r");                                    // HL7 Segment separator is \r (Carriage Return)

            // Construct MSA segment of ACK (Message Acknowledgment)
            ack.append("MSA").append(fieldSepChar)
               .append("AA").append(fieldSepChar)                // AA = Application Accept (Success)
               .append(messageControlId)
               .append("\r");

            return ack.toString();
        } catch (Exception e) {
            return generateGenericAck("UNKNOWN_ID", "AE", "Error parsing message: " + e.getMessage());
        }
    }

    /**
     * Generates a generic error ACK message.
     */
    private static String generateGenericAck(String controlId, String ackCode, String text) {
        String currentDateTime = LocalDateTime.now().format(DATE_FORMATTER);
        return "MSH|^~\\&|HL7_RECEIVER||||" + currentDateTime + "||ACK|" + controlId + "|P|2.3.1\r" +
               "MSA|" + ackCode + "|" + controlId + "|" + text + "\r";
    }
}
