package com.airplus.cilium.crd;

import java.util.List;

public class ServiceGroupSpec {
    private List<String> serviceNames;

    public List<String> getServiceNames() {
        return serviceNames;
    }

    public void setServiceNames(List<String> serviceNames) {
        this.serviceNames = serviceNames;
    }
}
