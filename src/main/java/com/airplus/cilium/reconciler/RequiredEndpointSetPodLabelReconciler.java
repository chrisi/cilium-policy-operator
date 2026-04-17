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
@ControllerConfiguration(generationAwareEventProcessing = false)
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

        // Identify labels that should be removed (predefined-endpoint labels that are no longer required)
        boolean hasLabelsToRemove = false;
        for (String key : podLabels.keySet()) {
            if (key.startsWith("com.airplus.cilium.predefined-endpoint/") && !labelsToAdd.containsKey(key)) {
                hasLabelsToRemove = true;
                break;
            }
        }

        if (labelsToAdd.isEmpty() && !hasLabelsToRemove) {
            return UpdateControl.noUpdate();
        }

        // Check if labels are already present/removed to avoid infinite reconciliation loops
        boolean needsUpdate = false;
        
        // Check for missing or differing labels
        for (Map.Entry<String, String> entry : labelsToAdd.entrySet()) {
            if (!entry.getValue().equals(podLabels.get(entry.getKey()))) {
                needsUpdate = true;
                break;
            }
        }
        
        // Check for labels that should be gone
        if (!needsUpdate && hasLabelsToRemove) {
            needsUpdate = true;
        }

        if (needsUpdate) {
            log.info("Updating predefined endpoint labels for Pod {} in namespace {}", name, namespace);
            
            // For Server-Side Apply, we only include the labels we want to manage/add.
            // Labels that we previously managed but are now missing from the patch will be removed by K8s SSA.
            // This avoids sending null values which might be problematic or redundant.
            
            Pod patch = new PodBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .withLabels(labelsToAdd)
                    .endMetadata()
                    .build();

            PatchContext patchContext = PatchContext.of(PatchType.SERVER_SIDE_APPLY);
            patchContext.setForce(true); //TODO: impl opt in
            client.pods().inNamespace(namespace).withName(name).patch(patchContext, patch);
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
