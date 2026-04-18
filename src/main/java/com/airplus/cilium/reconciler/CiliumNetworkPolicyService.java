package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.Endpoint;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.airplus.cilium.reconciler.Global.CCNP;
import static com.airplus.cilium.reconciler.Global.CILIO;

@Service
public class CiliumNetworkPolicyService {

  public GenericKubernetesResource createCiliumNetworkPolicy(Endpoint endpoint, OwnerReference ownRef) {
    String name = endpoint.getName();
    String address = endpoint.getAddress();
    String protocol = endpoint.getProtocol();
    String port = endpoint.getPort();

    List<Map<String, Object>> egressRules = ConvertUtils.convertTarget(address, port, protocol);

    return new GenericKubernetesResourceBuilder()
        .withApiVersion(CILIO).withKind(CCNP).withNewMetadata().withName(name)
        .addToLabels(Global.MANAGED_BY_LABEL_KEY, Global.MANAGED_BY_LABEL_VALUE)
        .addToOwnerReferences(ownRef)
        .endMetadata()
        .addToAdditionalProperties("spec", Map.of(
            "description", "L3 policy for " + name,
            "egress", egressRules,
            "endpointSelector", Map.of("matchLabels", Map.of("network-policy-predefined-" + name, "enabled"))
        )).build();
  }

}
