package com.airplus.cilium.reconciler;

import com.airplus.cilium.crd.Endpoint;
import io.fabric8.kubernetes.api.model.*;

import java.util.List;
import java.util.Map;

import static com.airplus.cilium.reconciler.Global.*;

public class K8sUtils {

  public static GenericKubernetesResource createCiliumNetworkPolicy(Endpoint endpoint, OwnerReference ownRef, String namespace, Map<String, String> endpointSelector) {
    String name = endpoint.getName();
    String address = endpoint.getAddress();
    String protocol = endpoint.getProtocol();
    String port = endpoint.getPort();

    List<Map<String, Object>> egressRules = ConvertUtils.convertTarget(address, port, protocol);

    var resName = namespace != null ? CNP : CCNP;

    var metaBuilder = new ObjectMetaBuilder().
        withName(name)
        .addToLabels(Global.MANAGED_BY_LABEL_KEY, Global.MANAGED_BY_LABEL_VALUE)
        .addToOwnerReferences(ownRef);

    if (namespace != null) {
      metaBuilder.withNamespace(namespace);
    }

    if (endpointSelector == null) {
      endpointSelector = Map.of(POLICY_LABEL_PREFIX + name, "enabled");
    }

    var builder = new GenericKubernetesResourceBuilder()
        .withApiVersion(CILIO).withKind(resName).withMetadata(metaBuilder.build())
        .addToAdditionalProperties("spec", Map.of(
            "description", "L3 policy for " + name,
            "egress", egressRules,
            "endpointSelector", Map.of("matchLabels", endpointSelector)
        ));

    return builder.build();
  }

  public static OwnerReference createOwnerReference(HasMetadata resource) {
    return new OwnerReferenceBuilder()
        .withApiVersion(resource.getApiVersion())
        .withKind(resource.getKind())
        .withName(resource.getMetadata().getName())
        .withUid(resource.getMetadata().getUid())
        .withController(true)
        .withBlockOwnerDeletion(true)
        .build();
  }

  public static boolean podLabelsMatch(Map<String, String> podLabels, Map<String, String> targetMatchLabels) {
    for (var entry : targetMatchLabels.entrySet()) {
      var podValue = podLabels.get(entry.getKey());
      if (podValue == null || !podValue.equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }
}
