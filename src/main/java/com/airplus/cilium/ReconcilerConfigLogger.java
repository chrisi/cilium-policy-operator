package com.airplus.cilium;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReconcilerConfigLogger {

  @Value("${operator.reconcilers.predefined-endpoint-catalog.update-interval:60}")
  private long predefinedEndpointCatalogInterval;

  @Value("${operator.reconcilers.required-endpoint-set.update-interval:60}")
  private long requiredEndpointSetInterval;

  @EventListener(ApplicationReadyEvent.class)
  public void logConfigurationOnStartup() {
    log.info("========================================");
    log.info("Cilium Policy Operator Configuration");
    log.info("========================================");
    log.info("PredefinedEndpointCatalog update interval: {} seconds", predefinedEndpointCatalogInterval);
    log.info("RequiredEndpointSet update interval: {} seconds", requiredEndpointSetInterval);
    log.info("========================================");
  }
}