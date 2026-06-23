package com.manipal.hl7;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface for implementing message-type-specific processing rules.
 * Implementations should register as CDI beans using @Named("MESSAGE_TYPE") (e.g. @Named("SIU")).
 */
public interface Hl7MessageProcessor {

    /**
     * Customizes or enriches the Canonical JSON payload for the specific message type.
     *
     * @param canonicalJsonNode The jackson ObjectNode representing the Canonical JSON structure.
     */
    void process(ObjectNode canonicalJsonNode);
}
