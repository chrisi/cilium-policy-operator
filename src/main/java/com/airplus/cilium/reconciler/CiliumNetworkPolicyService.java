package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.TargetSystemEntry;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CiliumNetworkPolicyService {

  public GenericKubernetesResource createCiliumNetworkPolicy(TargetSystemEntry entry, OwnerReference ownRef) {
    String name = entry.getName();
    String address = entry.getAddress();
    String protocol = entry.getProtocol();
    String port = entry.getPort();

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

    return new GenericKubernetesResourceBuilder()
        .withApiVersion("cilium.io/v2")
        .withKind("CiliumClusterwideNetworkPolicy")
        .withNewMetadata()
        .withName(name)
        .addToLabels(Global.MANAGED_BY_LABEL_KEY, Global.MANAGED_BY_LABEL_VALUE)
        .addToOwnerReferences(ownRef)
        .endMetadata()
        .addToAdditionalProperties("spec", Map.of(
            "description", "L3 policy for " + name,
            "egress", List.of(egressRule),
            "endpointSelector", Map.of("matchLabels", Map.of("network-policy-predefined-" + name, "enabled"))
        )).build();
  }

}
