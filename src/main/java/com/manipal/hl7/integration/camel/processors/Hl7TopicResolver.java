package com.manipal.hl7.integration.camel.processors;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Processor that parses the message type from raw HL7 MSH segment
 * and dynamically resolves the target Kafka topic from environment configurations.
 * It also extracts the Message Control ID to use as a Correlation ID.
 */
@ApplicationScoped
public class Hl7TopicResolver implements Processor {

    private static final Logger LOG = Logger.getLogger(Hl7TopicResolver.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        String rawHl7 = exchange.getIn().getBody(String.class);
        
        String messageType = "DEFAULT";
        String messageControlId = "UNKNOWN_ID";

        if (rawHl7 != null) {
            // Remove MLLP framing characters for clean parsing
            String clean = rawHl7.replace("\u000b", "").replace("\u001c", "").trim();
            String[] segments = clean.split("[\\r\\n]+");
            
            for (String segment : segments) {
                String trimmed = segment.trim();
                if (trimmed.startsWith("MSH")) {
                    char fieldSep = trimmed.charAt(3);
                    String escapedSep = (fieldSep == '|' || fieldSep == '^' || fieldSep == '$' || fieldSep == '\\') 
                        ? "\\" + fieldSep 
                        : String.valueOf(fieldSep);
                    
                    String[] fields = trimmed.split(escapedSep, -1);
                    
                    // MSH-9 (Message Type) is index 8 (since restOfSeg offset by 2, but here we split whole segment)
                    // fields[0] = "MSH"
                    // fields[1] = separator (or encoding chars since MSH-1 is separator, fields[1] is encoding chars)
                    // fields[2] = sending app, etc.
                    // fields[8] = message type
                    if (fields.length > 8) {
                        String msgTypeField = fields[8];
                        if (msgTypeField.contains("^")) {
                            messageType = msgTypeField.split("\\^")[0];
                        } else {
                            messageType = msgTypeField;
                        }
                    }
                    
                    // MSH-10 (Message Control ID) is index 9
                    if (fields.length > 9) {
                        messageControlId = fields[9];
                    }
                    break;
                }
            }
        }
        
        exchange.setProperty("messageType", messageType);
        exchange.getIn().setHeader("CorrelationId", messageControlId);
        
        // Put Correlation ID into Thread Context MDC for structured logging
        org.slf4j.MDC.put("correlationId", messageControlId);

        // Resolve topic from environment variables (maps "kafka.topic.<type>")
        String propertyKey = "kafka.topic." + messageType.toLowerCase();
        
        String targetTopic = ConfigProvider.getConfig()
            .getOptionalValue(propertyKey, String.class)
            .orElseGet(() -> ConfigProvider.getConfig().getValue("kafka.topic.default", String.class));

        LOG.infof("Routing message type %s [Correlation ID: %s] to Kafka topic: %s", messageType, messageControlId, targetTopic);
        
        // Set Camel Kafka header to override the topic name
        exchange.getIn().setHeader(KafkaConstants.OVERRIDE_TOPIC, targetTopic);
    }
}
