package com.manipal.hl7.integration.camel.routes;

import com.manipal.hl7.integration.camel.processors.Hl7TopicResolver;
import com.manipal.hl7.integration.registry.Hl7ProcessorRegistry;
import com.manipal.hl7.integration.camel.transformers.Hl7AckGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Route builder that configures Apache Camel routes for receiving HL7 messages,
 * transforming them to Canonical JSON, and forwarding them to dynamically resolved
 * Kafka topics asynchronously.
 */
@ApplicationScoped
public class Hl7RouteBuilder extends RouteBuilder {

    @ConfigProperty(name = "hl7.mllp.enabled", defaultValue = "true")
    boolean mllpEnabled;

    @Inject
    Hl7TopicResolver topicResolver;

    @Inject
    Hl7ProcessorRegistry processorRegistry;

    @Override
    public void configure() throws Exception {
        // Enable Mapped Diagnostic Context (MDC) logging in Camel for thread propagation
        getContext().setUseMDCLogging(true);

        // Global error handler to capture issues and log them properly (returns standard HL7 AE error ACK)
        onException(Exception.class)
            .handled(true)
            .log(org.apache.camel.LoggingLevel.ERROR, "Error occurred in HL7 route: ${exception.stacktrace}")
            .setHeader("CamelHttpResponseCode", constant(500))
            .setHeader("Content-Type", constant("text/plain"))
            .setBody(simple("MSH|^~\\&|HL7_RECEIVER||||${date:now:yyyyMMddHHmmss}||ACK||P|2.3.1\rMSA|AE||Failed to process HL7 message: ${exception.message}\r"));

        // ==========================================
        // 1. HTTP REST API Receiver
        // ==========================================
        from("platform-http:/api/hl7?httpMethodRestrict=POST")
            .routeId("hl7-http-receiver")
            .log("Received HL7 message via HTTP POST request")
            
            // WireTap routes the message to SEDA asynchronously (runs on a separate thread pool)
            // The client gets the HTTP response immediately without waiting for Kafka publishing.
            .wireTap("seda:publishToKafka")
            
            // Transform the body using the manual HL7 ACK generator
            .transform().body(String.class, Hl7AckGenerator::generateAck)
            
            // Prepare response for HTTP client (standard plain text HL7 response)
            .setHeader("CamelHttpResponseCode", constant(200))
            .setHeader("Content-Type", constant("text/plain"));

        // ==========================================
        // 2. MLLP TCP Server Receiver (Standard HL7)
        // ==========================================
        if (mllpEnabled) {
            from("netty:tcp://0.0.0.0:{{hl7.mllp.port}}?sync=true&decoders=#hl7decoder&encoders=#hl7encoder")
                .routeId("hl7-mllp-receiver")
                .log("Received HL7 message via MLLP TCP connection")
                
                // WireTap to process asynchronously and send to Kafka on a separate thread pool
                // The connection returns the MLLP ACK immediately to the sender.
                .wireTap("seda:publishToKafka")
                
                // Generate and return HL7 ACK (acknowledgment) immediately
                .transform().body(String.class, Hl7AckGenerator::generateAck)
                .log("Sent MLLP ACK response to caller");
        }

        // ==========================================
        // 3. Asynchronous Kafka Publisher
        // ==========================================
        // SEDA queue buffers messages and processes them on a separate thread pool.
        from("seda:publishToKafka?concurrentConsumers={{kafka.concurrent.consumers:5}}")
            .routeId("hl7-kafka-publisher")
            .log("Processing HL7 message for Kafka on thread: ${threadName}")
            
            // 3.1. Extract message type and resolve target Kafka topic dynamically
            .process(topicResolver)
            
            // 3.2. Ensure the CorrelationId is populated in MDC on the SEDA consumer thread
            .process(exchange -> {
                String correlationId = exchange.getIn().getHeader("CorrelationId", String.class);
                if (correlationId != null) {
                    org.slf4j.MDC.put("correlationId", correlationId);
                }
            })
            
            // 3.3. Transform the raw HL7 body into enriched Canonical JSON string
            .setBody(exchange -> processorRegistry.processAndConvert(
                exchange.getIn().getBody(String.class),
                exchange.getProperty("messageType", String.class)
            ))
            
            .log("Publishing Canonical JSON to Kafka...")
            
            // 3.4. Send payload to Kafka using the dynamically resolved override topic
            .to("kafka:default-topic?brokers={{kafka.brokers}}")
            .log("Successfully published message to Kafka!");
    }
}
