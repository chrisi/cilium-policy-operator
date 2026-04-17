package com.airplus.cilium.crd;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class RequiredEndpointSetSpec {
  private Map<String, String> targetMatchLabels;
  private List<String> predefinedEndpoints;
  private List<Endpoint> customEndpoints;
}
