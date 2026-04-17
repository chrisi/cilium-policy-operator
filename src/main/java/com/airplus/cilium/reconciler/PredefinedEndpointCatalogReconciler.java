package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.PredefinedEndpointCatalog;
import com.airplus.cilium.crd.Endpoint;
import com.airplus.cilium.crd.PredefinedEndpointCatalogStatus;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ControllerConfiguration
@RequiredArgsConstructor
@Slf4j
public class PredefinedEndpointCatalogReconciler implements Reconciler<PredefinedEndpointCatalog> {

  private final KubernetesClient client;
  private final CiliumNetworkPolicyService policyService;

  @Override
  public UpdateControl<PredefinedEndpointCatalog> reconcile(PredefinedEndpointCatalog resource, Context<PredefinedEndpointCatalog> context) {
    String name = resource.getMetadata().getName();
    String namespace = resource.getMetadata().getNamespace();
    log.info("Reconciling PredefinedEndpointCatalog: {} in namespace: {}", name, namespace);

    UpdateControl<PredefinedEndpointCatalog> updateControl = UpdateControl.patchStatus(resource);
    updateControl.rescheduleAfter(60, java.util.concurrent.TimeUnit.SECONDS);

    var endpoints = resource.getSpec().getEndpoints();
    if (endpoints == null) {
      endpoints = List.of();
    }

    // 1. Collect names of endpoints in the current spec
    var curEndpointsName = endpoints.stream()
        .map(Endpoint::getName)
        .collect(Collectors.toSet());

    // 2. List all CCNPs and filter those owned by this TargetSystem and managed by this operator
    String uid = resource.getMetadata().getUid();
    client.genericKubernetesResources("cilium.io/v2", "CiliumClusterwideNetworkPolicy")
        .withLabel(Global.MANAGED_BY_LABEL_KEY, Global.MANAGED_BY_LABEL_VALUE)
        .list().getItems().stream()
        .filter(ccnp -> ccnp.getMetadata().getOwnerReferences().stream()
            .anyMatch(ownerReference -> uid.equals(ownerReference.getUid())))
        .filter(ccnp -> !curEndpointsName.contains(ccnp.getMetadata().getName()))
        .forEach(ccnp -> {
          log.info("Deleting orphaned CCNP: {}", ccnp.getMetadata().getName());
          client.genericKubernetesResources("cilium.io/v2", "CiliumClusterwideNetworkPolicy")
              .resource(ccnp)
              .delete();
        });

    if (endpoints.isEmpty()) {
      log.warn("No entry provided for Cala: {}", name);
      return UpdateControl.noUpdate();
    }

    var ownRef = new OwnerReferenceBuilder()
        .withApiVersion(resource.getApiVersion())
        .withKind(resource.getKind())
        .withName(resource.getMetadata().getName())
        .withUid(resource.getMetadata().getUid())
        .withController(true)
        .withBlockOwnerDeletion(true)
        .build();

    for (var endpoint : endpoints) {
      var ccnp = policyService.createCiliumNetworkPolicy(endpoint, ownRef);

      client.genericKubernetesResources("cilium.io/v2", "CiliumClusterwideNetworkPolicy")
          .resource(ccnp)
          .serverSideApply();

      log.info("CCNP {} created", endpoint.getName());
    }

    if (resource.getStatus() == null) {
      resource.setStatus(new PredefinedEndpointCatalogStatus());
    }
    resource.getStatus().setStatus("Ready");

    return updateControl;
  }
}
