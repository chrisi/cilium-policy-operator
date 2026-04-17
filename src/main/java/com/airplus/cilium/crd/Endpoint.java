package com.airplus.cilium.crd;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Endpoint {
    private String name;
    private String address;
    private String port;
    private String protocol;
}
