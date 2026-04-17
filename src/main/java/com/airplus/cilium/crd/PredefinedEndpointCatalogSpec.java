package com.airplus.cilium.crd;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PredefinedEndpointCatalogSpec {
    private List<Endpoint> endpoints;
}
