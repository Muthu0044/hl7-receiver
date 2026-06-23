package com.manipal.hl7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to convert raw HL7 messages into a Canonical JSON structure.
 * It dynamically processes any HL7 message type and segment.
 */
@ApplicationScoped
public class Hl7ToCanonicalJsonConverter {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Converts a raw HL7 message into a Canonical JSON representation.
     */
    public String convert(String rawHl7) {
        try {
            // Clean MLLP framing characters from raw input
            String cleanHl7 = rawHl7.replace("\u000b", "").replace("\u001c", "").trim();
            String[] segments = cleanHl7.split("[\\r\\n]+");

            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode metadataNode = root.putObject("metadata");
            ObjectNode segmentsNode = root.putObject("segments");

            // LinkedHashMap preserves segment order. Map stores list of segment nodes to handle duplicates.
            Map<String, List<ObjectNode>> segmentMap = new LinkedHashMap<>();

            for (String segStr : segments) {
                segStr = segStr.trim();
                if (segStr.isEmpty()) {
                    continue;
                }

                String segName = segStr.substring(0, 3);
                char fieldSep = segStr.charAt(3);
                String restOfSeg = segStr.substring(4);

                ObjectNode segJson = objectMapper.createObjectNode();

                // MSH segment is parsed differently because the field separator itself is the first field (MSH-1)
                if ("MSH".equals(segName)) {
                    segJson.put("1", String.valueOf(fieldSep));
                    
                    String escapedSep = escapeRegex(fieldSep);
                    String[] fields = restOfSeg.split(escapedSep, -1);
                    
                    for (int i = 0; i < fields.length; i++) {
                        String fieldVal = fields[i];
                        int fieldIdx = i + 2; // Offset by 2 (MSH-1 is separator, MSH-2 is encoding chars)
                        parseField(segJson, String.valueOf(fieldIdx), fieldVal);
                    }
                    
                    // Extract metadata fields for the JSON root
                    String messageTypeField = segJson.path("9").asText(""); // e.g. SIU^S12
                    String msgType = "";
                    String triggerEvent = "";
                    if (messageTypeField.contains("^")) {
                        String[] parts = messageTypeField.split("\\^");
                        msgType = parts[0];
                        triggerEvent = parts.length > 1 ? parts[1] : "";
                    } else {
                        msgType = messageTypeField;
                    }

                    metadataNode.put("messageType", msgType);
                    metadataNode.put("triggerEvent", triggerEvent);
                    metadataNode.put("messageControlId", segJson.path("10").asText(""));
                    metadataNode.put("version", segJson.path("12").asText(""));

                } else {
                    String escapedSep = escapeRegex(fieldSep);
                    String[] fields = restOfSeg.split(escapedSep, -1);
                    
                    for (int i = 0; i < fields.length; i++) {
                        String fieldVal = fields[i];
                        int fieldIdx = i + 1;
                        parseField(segJson, String.valueOf(fieldIdx), fieldVal);
                    }
                }

                segmentMap.computeIfAbsent(segName, k -> new ArrayList<>()).add(segJson);
            }

            // Nest the segment lists under "segments" in the JSON payload
            for (Map.Entry<String, List<ObjectNode>> entry : segmentMap.entrySet()) {
                ArrayNode arrNode = segmentsNode.putArray(entry.getKey());
                for (ObjectNode obj : entry.getValue()) {
                    arrNode.add(obj);
                }
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert HL7 to Canonical JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a field value, handling repetitions (~) and components (^).
     */
    private void parseField(ObjectNode segJson, String key, String value) {
        if (value == null || value.isEmpty()) {
            segJson.put(key, "");
            return;
        }

        // Handle repetitions (~)
        if (value.contains("~")) {
            String[] reps = value.split("~", -1);
            ArrayNode repArray = objectMapper.createArrayNode();
            for (String rep : reps) {
                parseComponentOrValue(repArray, rep);
            }
            segJson.set(key, repArray);
        } else {
            // Handle components (^)
            if (value.contains("^")) {
                ObjectNode compObj = objectMapper.createObjectNode();
                String[] components = value.split("\\^", -1);
                for (int i = 0; i < components.length; i++) {
                    String compVal = components[i];
                    if (!compVal.isEmpty()) {
                        compObj.put(String.valueOf(i + 1), compVal);
                    }
                }
                segJson.set(key, compObj);
            } else {
                segJson.put(key, value);
            }
        }
    }

    private void parseComponentOrValue(ArrayNode repArray, String value) {
        if (value.contains("^")) {
            ObjectNode compObj = objectMapper.createObjectNode();
            String[] components = value.split("\\^", -1);
            for (int i = 0; i < components.length; i++) {
                String compVal = components[i];
                if (!compVal.isEmpty()) {
                    compObj.put(String.valueOf(i + 1), compVal);
                }
            }
            repArray.add(compObj);
        } else {
            repArray.add(value);
        }
    }

    private String escapeRegex(char c) {
        if (c == '|' || c == '^' || c == '$' || c == '\\' || c == '*' || c == '+' || c == '?' || c == '.') {
            return "\\" + c;
        }
        return String.valueOf(c);
    }
}
