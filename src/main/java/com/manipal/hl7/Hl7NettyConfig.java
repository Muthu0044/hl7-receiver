package com.manipal.hl7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.apache.camel.component.hl7.HL7MLLPNettyDecoderFactory;
import org.apache.camel.component.hl7.HL7MLLPNettyEncoderFactory;

/**
 * Configuration class that produces Netty encoder and decoder beans
 * for processing HL7 MLLP (Minimal Lower Layer Protocol) messages.
 */
@ApplicationScoped
public class Hl7NettyConfig {

    /**
     * Decoder factory that handles stripping MLLP framing characters from incoming TCP packets.
     */
    @Produces
    @Named("hl7decoder")
    public HL7MLLPNettyDecoderFactory hl7Decoder() {
        HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
        decoder.setConvertLFtoCR(true); // Normalize Line Feeds to Carriage Returns
        return decoder;
    }

    /**
     * Encoder factory that wraps outgoing messages (e.g., HL7 ACKs) with MLLP framing characters.
     */
    @Produces
    @Named("hl7encoder")
    public HL7MLLPNettyEncoderFactory hl7Encoder() {
        HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();
        return encoder;
    }
}
