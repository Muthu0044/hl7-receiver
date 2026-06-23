package com.manipal.hl7.integration.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Health check that validates Camel Context liveness status.
 */
@Liveness
@ApplicationScoped
public class CamelContextHealthCheck implements HealthCheck {

    @Inject
    CamelContext camelContext;

    @Override
    public HealthCheckResponse call() {
        boolean started = camelContext.getStatus().isStarted();
        return HealthCheckResponse.named("Camel Context Liveness")
            .status(started)
            .withData("status", camelContext.getStatus().name())
            .withData("contextName", camelContext.getName())
            .withData("version", camelContext.getVersion())
            .build();
    }
}
