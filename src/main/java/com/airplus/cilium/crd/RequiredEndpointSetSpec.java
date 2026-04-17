package com.airplus.cilium.crd;

import java.util.List;

public class RequiredEndpointSetSpec {
  private List<String> predefinedEndpoints;
  private List<Endpoint> customEndpoints;
}
