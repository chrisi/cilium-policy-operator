package com.airplus.cilium.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;

import java.util.Map;

public class K8sUtils {

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
