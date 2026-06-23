package com.manipal.hl7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

/**
 * Registry that manages custom Hl7MessageProcessor implementations and coordinates
 * parsing and custom message enrichments.
 */
@ApplicationScoped
public class Hl7ProcessorRegistry {

    private static final Logger LOG = Logger.getLogger(Hl7ProcessorRegistry.class);

    @Inject
    @Any
    Instance<Hl7MessageProcessor> processors;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Hl7ToCanonicalJsonConverter converter;

    /**
     * Converts raw HL7 payload to Canonical JSON, checks if a custom processor matches
     * the message type, executes it, and returns the final JSON string.
     *
     * @param rawHl7 The raw incoming HL7 message string
     * @param messageType The message type (e.g., "SIU", "ADT")
     * @return The final Canonical JSON string
     */
    public String processAndConvert(String rawHl7, String messageType) {
        String json = converter.convert(rawHl7);
        
        if (messageType == null || messageType.isEmpty()) {
            return json;
        }

        try {
            // Check the CDI container dynamically for a named bean (case-insensitive)
            Instance<Hl7MessageProcessor> matching = processors.select(
                NamedLiteral.of(messageType.toUpperCase())
            );

            if (!matching.isUnsatisfied()) {
                Hl7MessageProcessor processor = matching.get();
                LOG.infof("Applying custom processor for message type: %s", messageType);
                
                ObjectNode rootNode = (ObjectNode) objectMapper.readTree(json);
                processor.process(rootNode);
                return objectMapper.writeValueAsString(rootNode);
            } else {
                LOG.debugf("No custom processor found for message type: %s. Using generic transformation.", messageType);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to apply custom processor for %s: %s. Using default transformation.", messageType, e.getMessage());
        }

        return json;
    }
}
