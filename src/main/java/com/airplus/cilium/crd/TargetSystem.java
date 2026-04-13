package com.airplus.cilium.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("cilium.airplus.com")
@Version("v1")
public class TargetSystem extends CustomResource<TargetSystemSpec, TargetSystemStatus> implements Namespaced {
}
