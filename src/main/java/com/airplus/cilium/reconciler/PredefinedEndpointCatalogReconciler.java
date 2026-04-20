package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.PredefinedEndpointCatalog;
import com.airplus.cilium.crd.Endpoint;
import com.airplus.cilium.crd.PredefinedEndpointCatalogStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static com.airplus.cilium.reconciler.Global.*;
import static com.airplus.cilium.reconciler.K8sUtils.createCiliumClusterwideNetworkPolicy;
import static com.airplus.cilium.reconciler.K8sUtils.createCiliumNetworkPolicy;

@Component
@ControllerConfiguration
@RequiredArgsConstructor
@Slf4j
public class PredefinedEndpointCatalogReconciler implements Reconciler<PredefinedEndpointCatalog> {

  private final KubernetesClient client;

  @Override
  public UpdateControl<PredefinedEndpointCatalog> reconcile(PredefinedEndpointCatalog catalog, Context<PredefinedEndpointCatalog> context) {
    String name = catalog.getMetadata().getName();
    String namespace = catalog.getMetadata().getNamespace();
    log.info("reconciling {} {} in namespace: {}", PEC, name, namespace);

    UpdateControl<PredefinedEndpointCatalog> updateControl = UpdateControl.patchStatus(catalog);
    updateControl.rescheduleAfter(60, java.util.concurrent.TimeUnit.SECONDS);

    var endpoints = catalog.getSpec().getEndpoints();

    if (endpoints == null || endpoints.isEmpty()) {
      log.warn("no entries provided in {} '{}'", PEC, name);
      return UpdateControl.noUpdate();
    }

    var allEndpointName = endpoints.stream().map(Endpoint::getName).collect(Collectors.toSet());

    log.info("processing endpoints from {} '{}'", PEC, name);

    // delete orphaned policies
    String catalogUid = catalog.getMetadata().getUid();
    client.genericKubernetesResources(CILIO, CCNP).withLabel(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE)
        .list().getItems().stream()
        .filter(ccnp -> ccnp.getMetadata().getOwnerReferences().stream()
            .anyMatch(or -> catalogUid.equals(or.getUid())))
        .filter(ccnp -> !allEndpointName.contains(ccnp.getMetadata().getName()))
        .forEach(ccnp -> {
          log.info("deleting orphaned {} '{}'", CCNP, ccnp.getMetadata().getName());
          client.genericKubernetesResources(CILIO, CCNP).resource(ccnp).delete();
        });

    // apply new policies or change existing ones
    for (var endpoint : endpoints) {
      var policy = createCiliumClusterwideNetworkPolicy(endpoint, K8sUtils.createOwnerReference(catalog));
      log.info("applying {} '{}'", CCNP, endpoint.getName());
      client.genericKubernetesResources(CILIO, CCNP).resource(policy).serverSideApply();
    }
    log.info("finished applying {} {}(s) from {} '{}'", endpoints.size(), CCNP, PEC, name);

    if (catalog.getStatus() == null) {
      catalog.setStatus(new PredefinedEndpointCatalogStatus());
    }
    catalog.getStatus().setStatus("Ready");

    return updateControl;
  }
}
