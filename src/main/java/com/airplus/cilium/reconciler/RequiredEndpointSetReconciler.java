package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.RequiredEndpointSet;
import com.airplus.cilium.crd.Endpoint;
import com.airplus.cilium.crd.RequiredEndpointSetStatus;
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
import static com.airplus.cilium.reconciler.K8sUtils.createCiliumNetworkPolicy;

@Component
@ControllerConfiguration
@RequiredArgsConstructor
@Slf4j
public class RequiredEndpointSetReconciler implements Reconciler<RequiredEndpointSet> {

  private final KubernetesClient client;

  @Override
  public UpdateControl<RequiredEndpointSet> reconcile(RequiredEndpointSet res, Context<RequiredEndpointSet> context) {
    String name = res.getMetadata().getName();
    String namespace = res.getMetadata().getNamespace();
    log.info("reconciling RequiredEndpointSet {} in namespace: {}", name, namespace);

    UpdateControl<RequiredEndpointSet> updateControl = UpdateControl.patchStatus(res);
    updateControl.rescheduleAfter(60, java.util.concurrent.TimeUnit.SECONDS);

    var customEndpoints = res.getSpec().getCustomEndpoints();

    if (customEndpoints == null || customEndpoints.isEmpty()) {
      log.info("no customEndpoints provided in RequiredEndpointSet '{}'", name);
    } else {
      var allEndpointName = customEndpoints.stream().map(Endpoint::getName).collect(Collectors.toSet());
      var targetMatchLabels = res.getSpec().getTargetMatchLabels();

      log.info("processing customEndpoints from RequiredEndpointSet '{}'", name);

      // delete orphaned policies
      String resUid = res.getMetadata().getUid();
      client.genericKubernetesResources(CILIO, CNP).inNamespace(namespace)
          .withLabel(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE)
          .list().getItems().stream()
          .filter(cnp -> cnp.getMetadata().getOwnerReferences().stream()
              .anyMatch(or -> resUid.equals(or.getUid())))
          .filter(cnp -> !allEndpointName.contains(cnp.getMetadata().getName()))
          .forEach(cnp -> {
            log.info("deleting orphaned {} '{}'", CNP, cnp.getMetadata().getName());
            client.genericKubernetesResources(CILIO, CNP).inNamespace(namespace).resource(cnp).delete();
          });

      // apply new policies or change existing ones
      for (var endpoint : customEndpoints) {
        var cnp = createCiliumNetworkPolicy(endpoint, K8sUtils.createOwnerReference(res), namespace, targetMatchLabels);
        log.info("applying {} '{}' in namespace '{}'", CNP, endpoint.getName(), namespace);
        client.genericKubernetesResources(CILIO, CNP).inNamespace(namespace).resource(cnp).serverSideApply();
      }
    }

    if (res.getStatus() == null) {
      res.setStatus(new RequiredEndpointSetStatus());
    }
    res.getStatus().setStatus("Ready");

    return updateControl;
  }
}
