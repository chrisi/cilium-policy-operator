package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.RequiredEndpointSet;
import com.airplus.cilium.crd.RequiredEndpointSetStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.airplus.cilium.reconciler.Global.*;
import static com.airplus.cilium.reconciler.K8sUtils.createCiliumNetworkPolicy;

@Component
@ControllerConfiguration
@Slf4j
public class RequiredEndpointSetReconciler implements Reconciler<RequiredEndpointSet> {

  @Value("${operator.reconcilers.required-endpoint-set.update-interval:60}")
  private long interval;

  private final KubernetesClient client;

  private final Counter reconcileCounter;

  public RequiredEndpointSetReconciler(KubernetesClient client, MeterRegistry registry) {
    this.client = client;
    this.reconcileCounter = Counter.builder("ciliumpolicyoperator_requiredendpointset_reconcile_total")
        .description("Total number of RequiredEndpointSet reconcile operations")
        .tag("status", "success")
        .register(registry);
  }

  @Override
  public UpdateControl<RequiredEndpointSet> reconcile(RequiredEndpointSet res, Context<RequiredEndpointSet> context) {
    String name = res.getMetadata().getName();
    String namespace = res.getMetadata().getNamespace();
    log.info("reconciling {} '{}' in namespace '{}'", RES, name, namespace);

    reconcileCounter.increment();

    UpdateControl<RequiredEndpointSet> updateControl = UpdateControl.patchStatus(res);
    updateControl.rescheduleAfter(interval, java.util.concurrent.TimeUnit.SECONDS);

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

  private void processRequiredEndpoints(RequiredEndpointSet res, String name, String namespace, String appName) {
    var customEndpoints = res.getSpec().getCustomEndpoints();
    if (customEndpoints == null) {
      customEndpoints = List.of();
    }

    var allEndpointName = customEndpoints.stream()
        .map(endpoint -> String.format("%s-%s", res.getMetadata().getName(), endpoint.getName()))
        .collect(Collectors.toSet());

    deleteOrphanedPolicies(res, namespace, pol -> !allEndpointName.contains(pol.getMetadata().getName()), CILIOv2, CNP);

    if (customEndpoints.isEmpty()) {
      log.info("no customEndpoints provided in {} '{}'", RES, name);
    } else {
      log.info("processing customEndpoints from {} '{}'", RES, name);
      for (var endpoint : customEndpoints) {
        var policyName = String.format("%s-%s", res.getMetadata().getName(), endpoint.getName());
        var policy = createCiliumNetworkPolicy(endpoint, K8sUtils.createOwnerReference(res), namespace, policyName, res.getSpec().getTargetMatchLabels());
        apply(policy, namespace, appName, policyName, CILIOv2, CNP);
      }
      log.info("finished applying {} {}(s) from {} '{}'", customEndpoints.size(), CNP, RES, name);
    }
  }

  private void processAllowedProcesses(RequiredEndpointSet res, String name, String namespace, String appName) {
    var allowedProcesses = res.getSpec().getAllowedProcesses();
    var policyName = String.format("%s-tracing", res.getMetadata().getName());

    deleteOrphanedPolicies(res, namespace, pol -> !pol.getMetadata().getName().equals(policyName), CILIOv1alpha1, TPN);

    if (allowedProcesses == null || allowedProcesses.isEmpty()) {
      log.info("no allowedProcesses provided in {} '{}'", RES, name);
    } else {
      log.info("processing allowedProcesses from {} '{}'", RES, name);
      var policy = K8sUtils.createTracingPolicyNamespaced(allowedProcesses, K8sUtils.createOwnerReference(res), namespace, policyName, res.getSpec().getTargetMatchLabels());
      apply(policy, namespace, appName, policyName, CILIOv1alpha1, TPN);
      log.info("finished applying {} from {} '{}'", TPN, RES, name);
    }
  }

  private void deleteOrphanedPolicies(RequiredEndpointSet res, String namespace, Predicate<GenericKubernetesResource> filter, String resGroup, String resKind) {
    client.genericKubernetesResources(resGroup, resKind).inNamespace(namespace)
        .withLabel(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE).list().getItems().stream()
        .filter(pol -> pol.getMetadata().getOwnerReferences().stream()
            .anyMatch(or -> or.getUid().equals(res.getMetadata().getUid())))
        .filter(filter)
        .forEach(pol -> {
          log.info("deleting orphaned {} '{}'", resKind, pol.getMetadata().getName());
          client.genericKubernetesResources(resGroup, resKind).inNamespace(namespace).resource(pol).delete();
        });
  }

  private void apply(GenericKubernetesResource policy, String namespace, String appName, String policyName, String resGroup, String resKind) {
    log.info("applying {} '{}' for '{}' in namespace '{}'", resKind, policyName, appName, namespace);
    try {
      client.genericKubernetesResources(resGroup, resKind).inNamespace(namespace).resource(policy).serverSideApply();
    } catch (Exception e) {
      log.error("failed to apply {} '{}': {}", resKind, policyName, e.getMessage());
    }
  }
}
