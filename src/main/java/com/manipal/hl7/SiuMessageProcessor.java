package com.manipal.hl7;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Custom processor for HL7 Scheduling Information Unsolicited (SIU) messages.
 */
@ApplicationScoped
@Named("SIU")
public class SiuMessageProcessor implements Hl7MessageProcessor {

    @Override
    public void process(ObjectNode canonicalJsonNode) {
        // Example custom processing: enrich the metadata block
        ObjectNode metadata = (ObjectNode) canonicalJsonNode.get("metadata");
        if (metadata != null) {
            metadata.put("enriched", true);
            metadata.put("processor", "SiuMessageProcessor");
        }
    }
}
