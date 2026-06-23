package com.manipal.hl7;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Processor that parses the message type from raw HL7 MSH segment
 * and dynamically resolves the target Kafka topic from environment configurations.
 */
@ApplicationScoped
public class Hl7TopicResolver implements Processor {

    private static final Logger LOG = Logger.getLogger(Hl7TopicResolver.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        String rawHl7 = exchange.getIn().getBody(String.class);
        
        // 1. Extract message type (MSH-9)
        String messageType = extractMessageType(rawHl7);
        exchange.setProperty("messageType", messageType);

        // 2. Resolve topic from environment variables (maps "kafka.topic.<type>")
        String propertyKey = "kafka.topic." + messageType.toLowerCase();
        
        String targetTopic = ConfigProvider.getConfig()
            .getOptionalValue(propertyKey, String.class)
            .orElseGet(() -> ConfigProvider.getConfig().getValue("kafka.topic.default", String.class));

        LOG.infof("Routing message type %s to Kafka topic: %s", messageType, targetTopic);
        
        // 3. Set Camel Kafka header to override the topic name
        exchange.getIn().setHeader(KafkaConstants.OVERRIDE_TOPIC, targetTopic);
    }

    /**
     * Extracts the message type from the raw HL7 MSH segment.
     */
    private String extractMessageType(String rawHl7) {
        if (rawHl7 == null) return "DEFAULT";
        
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
                if (fields.length > 8) {
                    String msgTypeField = fields[8];
                    if (msgTypeField.contains("^")) {
                        return msgTypeField.split("\\^")[0];
                    }
                    return msgTypeField;
                }
            }
        }
        return "DEFAULT";
    }
}
