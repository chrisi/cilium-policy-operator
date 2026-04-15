package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.TargetSystem;
import com.airplus.cilium.crd.TargetSystemEntry;
import com.airplus.cilium.crd.TargetSystemStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ControllerConfiguration
public class TargetSystemReconciler implements Reconciler<TargetSystem> {

    private static final Logger log = LoggerFactory.getLogger(TargetSystemReconciler.class);
    private final KubernetesClient client;

    public TargetSystemReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<TargetSystem> reconcile(TargetSystem resource, Context<TargetSystem> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        log.info("Reconciling TargetSystem: {} in namespace: {}", name, namespace);

        var targets = resource.getSpec().getTargets();
        if (targets == null) {
            targets = List.of();
        }

        // 1. Collect names of targets in the current spec
        Set<String> currentTargetNames = targets.stream()
                .map(TargetSystemEntry::getName)
                .collect(Collectors.toSet());

        // 2. List all CCNPs and filter those owned by this TargetSystem
        String uid = resource.getMetadata().getUid();
        client.genericKubernetesResources("cilium.io/v2", "CiliumClusterwideNetworkPolicy")
                .list().getItems().stream()
                .filter(ccnp -> ccnp.getMetadata().getOwnerReferences().stream()
                        .anyMatch(ownerReference -> uid.equals(ownerReference.getUid())))
                .filter(ccnp -> !currentTargetNames.contains(ccnp.getMetadata().getName()))
                .forEach(ccnp -> {
                    log.info("Deleting orphaned CCNP: {}", ccnp.getMetadata().getName());
                    client.genericKubernetesResources("cilium.io/v2", "CiliumClusterwideNetworkPolicy")
                            .resource(ccnp)
                            .delete();
                });

        if (targets.isEmpty()) {
            log.warn("No targets provided for TargetSystem: {}", name);
            return UpdateControl.noUpdate();
        }

        for (var target : targets) {
            String targetName = target.getName();
            String address = target.getAddress();
            String protocol = target.getProtocol();
            String port = target.getPort();

            // Based on the example provided:
            // apiVersion: cilium.io/v2
            // kind: CiliumClusterwideNetworkPolicy
            // metadata:
            //   name: <target-name>
            // spec:
            //   description: L3 policy for <portal>
            //   egress:
            //   - toFQDNs:
            //     - matchName: <address>
            //     toPorts:
            //     - ports:
            //       - port: "443" (Wait, what port? Use address for now or assume common if not specified?)
            //         protocol: <protocol>
            //   endpointSelector:
            //     matchLabels:
            //       network-policy-predefined-<target-name>: enabled

            // Since port wasn't in the CRD but was in the example, I'll use a placeholder or check if address contains it.
            // Actually, the example has port: "443". I'll try to find if it should be configurable.
            // For now, let's stick as close as possible to the example.

            Map<String, Object> egressRule = Map.of(
                    "toFQDNs", List.of(Map.of("matchName", address)),
                    "toPorts", List.of(Map.of(
                            "ports", List.of(Map.of(
                                    "port", port,
                                    "protocol", protocol != null ? protocol.toUpperCase() : "TCP"
                            ))
                    ))
            );

            // If it's an IP range, it might need 'toCIDR' instead of 'toFQDNs'
            if (address.matches("^[0-9./]+$")) {
                egressRule = Map.of(
                        "toCIDR", List.of(address),
                        "toPorts", List.of(Map.of(
                                "ports", List.of(Map.of(
                                        "port", "443",
                                        "protocol", protocol != null ? protocol.toUpperCase() : "TCP"
                                ))
                        ))
                );
            }

            GenericKubernetesResource ccnp = new GenericKubernetesResourceBuilder()
                    .withApiVersion("cilium.io/v2")
                    .withKind("CiliumClusterwideNetworkPolicy")
                    .withNewMetadata()
                        .withName(targetName)
                        .addNewOwnerReference()
                            .withApiVersion(resource.getApiVersion())
                            .withKind(resource.getKind())
                            .withName(name)
                            .withUid(resource.getMetadata().getUid())
                            .withController(true)
                            .withBlockOwnerDeletion(true)
                        .endOwnerReference()
                    .endMetadata()
                    .addToAdditionalProperties("spec", Map.of(
                            "description", "L3 policy for " + name,
                            "egress", List.of(egressRule),
                            "endpointSelector", Map.of("matchLabels", Map.of("network-policy-predefined-" + targetName, "enabled"))
                    ))
                    .build();

            client.genericKubernetesResources("cilium.io/v2", "CiliumClusterwideNetworkPolicy")
                    .resource(ccnp)
                    .serverSideApply();
            
            log.info("CCNP {} applied for target {}", targetName, targetName);
        }

        if (resource.getStatus() == null) {
            resource.setStatus(new TargetSystemStatus());
        }
        resource.getStatus().setStatus("Ready");

        return UpdateControl.patchStatus(resource);
    }
}
