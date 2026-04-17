package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.RequiredEndpointSet;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ControllerConfiguration
@RequiredArgsConstructor
@Slf4j
public class RequiredEndpointSetPodLabelReconciler implements Reconciler<Pod> {

    private final KubernetesClient client;

    @Override
    public UpdateControl<Pod> reconcile(Pod pod, Context<Pod> context) {
        String name = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();

        if (pod.getMetadata().getDeletionTimestamp() != null) {
            return UpdateControl.noUpdate();
        }

        Map<String, String> podLabels = pod.getMetadata().getLabels();
        if (podLabels == null) {
            podLabels = new HashMap<>();
        }

        List<RequiredEndpointSet> requiredEndpointSets = client.resources(RequiredEndpointSet.class)
                .inAnyNamespace()
                .list()
                .getItems();

        Map<String, String> labelsToAdd = new HashMap<>();

        for (RequiredEndpointSet res : requiredEndpointSets) {
            Map<String, String> targetMatchLabels = res.getSpec().getTargetMatchLabels();
            if (targetMatchLabels != null && !targetMatchLabels.isEmpty()) {
                if (podLabelsMatch(podLabels, targetMatchLabels)) {
                    List<String> predefinedEndpoints = res.getSpec().getPredefinedEndpoints();
                    if (predefinedEndpoints != null) {
                        for (String endpoint : predefinedEndpoints) {
                            labelsToAdd.put("com.airplus.cilium.predefined-endpoint/" + endpoint, "enabled");
                        }
                    }
                }
            }
        }

        if (labelsToAdd.isEmpty()) {
            return UpdateControl.noUpdate();
        }

        // Check if labels are already present to avoid infinite reconciliation loops
        boolean needsUpdate = false;
        for (Map.Entry<String, String> entry : labelsToAdd.entrySet()) {
            if (!entry.getValue().equals(podLabels.get(entry.getKey()))) {
                needsUpdate = true;
                break;
            }
        }

        if (needsUpdate) {
            log.info("Adding predefined endpoint labels to Pod {} in namespace {}", name, namespace);
            
            Pod patch = new PodBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .withLabels(labelsToAdd)
                    .endMetadata()
                    .build();

            client.pods().inNamespace(namespace).withName(name).patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY), patch);
        }

        return UpdateControl.noUpdate();
    }

    private boolean podLabelsMatch(Map<String, String> podLabels, Map<String, String> targetMatchLabels) {
        for (Map.Entry<String, String> entry : targetMatchLabels.entrySet()) {
            String podValue = podLabels.get(entry.getKey());
            if (podValue == null || !podValue.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
