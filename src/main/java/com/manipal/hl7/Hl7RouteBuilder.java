package com.manipal.hl7;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hl7.HL7;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Route builder that configures Apache Camel routes for receiving HL7 messages
 * and forwarding them to a Kafka broker asynchronously.
 */
@ApplicationScoped
public class Hl7RouteBuilder extends RouteBuilder {

    @ConfigProperty(name = "hl7.mllp.enabled", defaultValue = "true")
    boolean mllpEnabled;

    @Override
    public void configure() throws Exception {
        // Global error handler to capture issues and log them properly
        onException(Exception.class)
            .handled(true)
            .log(org.apache.camel.LoggingLevel.ERROR, "Error occurred in HL7 route: ${exception.stacktrace}")
            .setHeader("CamelHttpResponseCode", constant(500))
            .setHeader("Content-Type", constant("application/json"))
            .setBody(simple("{\"status\":\"ERROR\",\"message\":\"Failed to process HL7 message: ${exception.message}\"}"));

        // ==========================================
        // 1. HTTP REST API Receiver
        // ==========================================
        from("platform-http:/api/hl7?httpMethodRestrict=POST")
            .routeId("hl7-http-receiver")
            .log("Received HL7 message via HTTP POST request")
            
            // WireTap routes the message to SEDA asynchronously (runs on a separate thread pool)
            // The client gets the HTTP response immediately without waiting for Kafka publishing.
            .wireTap("seda:publishToKafka")
            
            // Prepare response for HTTP client
            .setHeader("CamelHttpResponseCode", constant(202))
            .setHeader("Content-Type", constant("application/json"))
            .setBody(constant("{\"status\":\"ACCEPTED\",\"message\":\"HL7 message received and is being processed asynchronously.\"}"));

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
                .transform(HL7.ack())
                .log("Sent MLLP ACK response to caller");
        }

        // ==========================================
        // 3. Asynchronous Kafka Publisher
        // ==========================================
        // seda queue buffers messages and processes them on a separate thread pool.
        from("seda:publishToKafka?concurrentConsumers={{kafka.concurrent.consumers:5}}")
            .routeId("hl7-kafka-publisher")
            .log("Processing HL7 message for Kafka on thread: ${threadName}")
            
            // Send the raw message body (as String) to Kafka
            .to("kafka:{{kafka.topic}}?brokers={{kafka.brokers}}")
            .log("Successfully published message to Kafka topic: {{kafka.topic}}");
    }
}
