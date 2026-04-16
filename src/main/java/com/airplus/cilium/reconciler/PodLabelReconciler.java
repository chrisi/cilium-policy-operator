package com.airplus.cilium.reconciler;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ControllerConfiguration
@RequiredArgsConstructor
@Slf4j
public class PodLabelReconciler implements Reconciler<Pod> {

    private final KubernetesClient client;

    @Override
    public UpdateControl<Pod> reconcile(Pod pod, Context<Pod> context) {
        String name = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();

        if (!"default".equals(namespace)) {
            return UpdateControl.noUpdate();
        }

        Map<String, String> labels = pod.getMetadata().getLabels();
        if (labels == null) {
            labels = new HashMap<>();
        }

        if (!"welt".equals(labels.get("hallo"))) {
            log.info("Adding label 'hallo:welt' to Pod {} in namespace {}", name, namespace);
            
            client.pods().inNamespace(namespace).withName(name).edit(p -> {
                Map<String, String> currentLabels = p.getMetadata().getLabels();
                if (currentLabels == null) {
                    currentLabels = new HashMap<>();
                }
                currentLabels.put("hallo", "welt");
                p.getMetadata().setLabels(currentLabels);
                return p;
            });

            return UpdateControl.noUpdate();
        }

        return UpdateControl.noUpdate();
    }
}
