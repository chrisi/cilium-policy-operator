package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.RequiredEndpointSet;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.airplus.cilium.reconciler.Global.POLICY_LABEL_PREFIX;
import static com.airplus.cilium.reconciler.K8sUtils.podLabelsMatch;

@Component
@ControllerConfiguration(generationAwareEventProcessing = false)
@Slf4j
public class PodRequiredEndpointLabelReconciler implements Reconciler<Pod> {

  private final KubernetesClient client;

  private final Counter reconcileCounter;

  public PodRequiredEndpointLabelReconciler(KubernetesClient client, MeterRegistry registry) {
    this.client = client;
    this.reconcileCounter = Counter.builder("ciliumpolicyoperator_podrequiredendpointlabel_reconcile_total")
        .description("Total number of PodRequiredEndpointLabel reconcile operations")
        .tag("status", "success")
        .register(registry);
  }

  @Override
  public List<EventSource<?, Pod>> prepareEventSources(EventSourceContext<Pod> context) {
    var cfg = InformerEventSourceConfiguration.from(RequiredEndpointSet.class, Pod.class)
        .withSecondaryToPrimaryMapper(res -> {
          var labels = res.getSpec().getTargetMatchLabels();
          if (labels == null || labels.isEmpty()) {
            return Collections.emptySet();
          }
          return client.pods().inAnyNamespace().withLabels(labels).list().getItems().stream()
              .map(ResourceID::fromResource).collect(Collectors.toSet());
        })
        //TODO: detaching of the RequiredEndpointSets from the pod by renaming the targetSelectorLables is not yet implemented.
        .withOnDeleteFilter((res, deletedFinalStateUnknown) -> true).build();

    return List.of(new InformerEventSource<>(cfg, context));
  }

  @Override
  public UpdateControl<Pod> reconcile(Pod pod, Context<Pod> context) {

    if (pod.getMetadata().getDeletionTimestamp() != null) {
      return UpdateControl.noUpdate();
    }

    var name = pod.getMetadata().getName();
    var namespace = pod.getMetadata().getNamespace();

    final var podLabels = pod.getMetadata().getLabels();
    if (podLabels == null || podLabels.isEmpty()) {
      log.debug("pod '{}' in namespace '{}' has no labels so cannot be targeted", name, namespace);
      return UpdateControl.noUpdate();
    }

    reconcileCounter.increment();

    // Find all RequiredEndpointSets that are relevant for this pod
    var sets = client.resources(RequiredEndpointSet.class).list().getItems().stream()
        .filter(res -> podLabelsMatch(podLabels, res.getSpec().getTargetMatchLabels())).toList();

    // Collect labels that should be added
    var labelsToAdd = new HashMap<String, String>();
    for (var set : sets) {
      var eps = set.getSpec().getPredefinedEndpoints();
      if (eps != null) {
        for (String ep : eps) {
          labelsToAdd.put(POLICY_LABEL_PREFIX + ep, "enabled");
        }
      }
    }

    // Check if there are labels that should be removed, since Server-Side Apply automatically removes
    // labels that are not present in the patch, we don't need to collect them but just trigger an update
    boolean hasLabelsToRemove = false;
    for (var key : podLabels.keySet()) {
      if (key.startsWith(POLICY_LABEL_PREFIX) && !labelsToAdd.containsKey(key)) {
        hasLabelsToRemove = true;
        break;
      }
    }

    // End reconcile here if nothing to do
    if (labelsToAdd.isEmpty() && !hasLabelsToRemove) {
      return UpdateControl.noUpdate();
    }

    boolean labelsChanged = false;
    for (var entry : labelsToAdd.entrySet()) {
      if (!entry.getValue().equals(podLabels.get(entry.getKey()))) {
        labelsChanged = true;
        break;
      }
    }

    if (labelsChanged || hasLabelsToRemove) {
      log.info("updating predefined endpoint labels for pod '{}' in namespace '{}'", name, namespace);

      // For Server-Side Apply, we only include the labels we want to manage/add.
      // Labels that we previously managed but are now missing from the patch will be removed by K8s SSA.
      // This avoids sending null values which might be problematic or redundant.
      var patch = new PodBuilder()
          .withNewMetadata()
          .withName(name)
          .withNamespace(namespace)
          .withLabels(labelsToAdd)
          .endMetadata()
          .build();

      var ctx = PatchContext.of(PatchType.SERVER_SIDE_APPLY);
      ctx.setForce(true); //TODO: impl opt in
      client.pods().inNamespace(namespace).withName(name).patch(ctx, patch);
    }

    return UpdateControl.noUpdate();
  }
}
