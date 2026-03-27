package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.ServiceGroup;
import com.airplus.cilium.crd.ServiceGroupStatus;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyPeerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@ControllerConfiguration
public class ServiceGroupReconciler implements Reconciler<ServiceGroup> {

    private static final Logger log = LoggerFactory.getLogger(ServiceGroupReconciler.class);
    private final KubernetesClient client;

    public ServiceGroupReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<ServiceGroup> reconcile(ServiceGroup resource, Context<ServiceGroup> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        log.info("Reconciling ServiceGroup: {} in namespace: {}", name, namespace);

        var serviceNames = resource.getSpec().getServiceNames();
        if (serviceNames == null || serviceNames.isEmpty()) {
            log.warn("No service names provided for ServiceGroup: {}", name);
            return UpdateControl.noUpdate();
        }

        // Create a NetworkPolicy that allows ingress from the listed services
        // This is an example logic: allowing traffic to pods labelled app=<group-name>
        // from pods labelled app=<any-of-service-names>
        
        NetworkPolicy networkPolicy = new NetworkPolicyBuilder()
                .withNewMetadata()
                    .withName(name + "-policy")
                    .withNamespace(namespace)
                    .addNewOwnerReference()
                        .withApiVersion(resource.getApiVersion())
                        .withKind(resource.getKind())
                        .withName(name)
                        .withUid(resource.getMetadata().getUid())
                        .withController(true)
                        .withBlockOwnerDeletion(true)
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec()
                    .withNewPodSelector()
                        .addToMatchLabels("service-group", name)
                    .endPodSelector()
                    .withIngress(new NetworkPolicyIngressRuleBuilder()
                            .withFrom(serviceNames.stream()
                                    .map(svc -> new NetworkPolicyPeerBuilder()
                                            .withNewPodSelector()
                                                .addToMatchLabels("app", svc)
                                            .endPodSelector()
                                            .build())
                                    .collect(Collectors.toList()))
                            .build())
                    .withPolicyTypes("Ingress")
                .endSpec()
                .build();

        client.network().v1().networkPolicies().inNamespace(namespace).resource(networkPolicy).serverSideApply();

        log.info("NetworkPolicy {} updated/created for ServiceGroup {}", networkPolicy.getMetadata().getName(), name);

        if (resource.getStatus() == null) {
            resource.setStatus(new ServiceGroupStatus());
        }
        resource.getStatus().setStatus("Ready");

        return UpdateControl.patchStatus(resource);
    }
}
