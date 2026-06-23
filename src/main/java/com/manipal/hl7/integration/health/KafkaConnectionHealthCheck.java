package com.manipal.hl7.integration.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import java.util.Properties;

/**
 * Health check that validates Kafka connectivity during readiness probe.
 */
@Readiness
@ApplicationScoped
public class KafkaConnectionHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        String brokers = ConfigProvider.getConfig().getValue("kafka.brokers", String.class);
        
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000"); // 3 seconds timeout
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000");

        try (AdminClient client = AdminClient.create(props)) {
            // Attempt to query the cluster details to confirm broker is reachable and responsive
            client.describeCluster().clusterId().get();
            return HealthCheckResponse.named("Kafka Connection")
                .up()
                .withData("brokers", brokers)
                .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("Kafka Connection")
                .down()
                .withData("brokers", brokers)
                .withData("error", e.getMessage())
                .build();
        }
    }
}
