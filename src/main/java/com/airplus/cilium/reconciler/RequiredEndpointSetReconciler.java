package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.RequiredEndpointSet;
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
    log.info("reconciling {} {} in namespace: {}", RES, name, namespace);

    UpdateControl<RequiredEndpointSet> updateControl = UpdateControl.patchStatus(res);
    updateControl.rescheduleAfter(60, java.util.concurrent.TimeUnit.SECONDS);

    var targetMatchLabels = res.getSpec().getTargetMatchLabels();
    var appName = targetMatchLabels.get("app");
    if (appName == null) appName = targetMatchLabels.toString();

    processRequiredEndpoints(res, name, namespace, appName);

    processAllowedProcesses(res, name, namespace, appName);

    if (res.getStatus() == null) {
      res.setStatus(new RequiredEndpointSetStatus());
    }
    res.getStatus().setStatus("Ready");

    return updateControl;
  }

  private void processAllowedProcesses(RequiredEndpointSet res, String name, String namespace, String appName) {
    var allowedProcesses = res.getSpec().getAllowedProcesses();

    if (allowedProcesses == null || allowedProcesses.isEmpty()) {
      log.info("no allowedProcesses provided in {} '{}'", RES, name);
    } else {
      // create tracing policies here
    }
  }

  private void processRequiredEndpoints(RequiredEndpointSet res, String name, String namespace, String appName) {
    var customEndpoints = res.getSpec().getCustomEndpoints();

    if (customEndpoints == null || customEndpoints.isEmpty()) {
      log.info("no customEndpoints provided in {} '{}'", RES, name);
    } else {
      log.info("processing customEndpoints from {} '{}'", RES, name);

      var allEndpointName = customEndpoints.stream()
          .map(endpoint -> String.format("%s-%s", res.getMetadata().getName(), endpoint.getName()))
          .collect(Collectors.toSet());

      // delete orphaned policies
      String resUid = res.getMetadata().getUid();
      client.genericKubernetesResources(CILIOv2, CNP).inNamespace(namespace)
          .withLabel(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE)
          .list().getItems().stream()
          .filter(cnp -> cnp.getMetadata().getOwnerReferences().stream()
              .anyMatch(or -> resUid.equals(or.getUid())))
          .filter(cnp -> !allEndpointName.contains(cnp.getMetadata().getName()))
          .forEach(cnp -> {
            log.info("deleting orphaned {} '{}'", CNP, cnp.getMetadata().getName());
            client.genericKubernetesResources(CILIOv2, CNP).inNamespace(namespace).resource(cnp).delete();
          });

      // apply new policies or change existing ones
      for (var endpoint : customEndpoints) {
        var policyName = String.format("%s-%s", res.getMetadata().getName(), endpoint.getName());
        var policy = createCiliumNetworkPolicy(endpoint, K8sUtils.createOwnerReference(res), namespace, policyName, res.getSpec().getTargetMatchLabels());
        log.info("applying {} '{}' for '{}' in namespace '{}'", CNP, policyName, appName, namespace);
        try {
          client.genericKubernetesResources(CILIOv2, CNP).inNamespace(namespace).resource(policy).serverSideApply();
        } catch (Exception e) {
          log.error("failed to apply {} '{}': {}", CNP, policyName, e.getMessage());
        }
      }
      log.info("finished applying {} {}(s) from {} '{}'", customEndpoints.size(), CNP, RES, name);
    }
  }
}
