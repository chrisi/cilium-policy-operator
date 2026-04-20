package com.airplus.cilium.reconciler;

public class Global {
  public static final String MANAGED_BY_LABEL_KEY = "airplus.com/managed-by";
  public static final String MANAGED_BY_LABEL_VALUE = "cilium-policy-operator";

  public static final String POLICY_LABEL_PREFIX = "com.airplus.cilium.predefined-endpoint/";

  public static final String CILIO = "cilium.io/v2";
  public static final String CCNP = "CiliumClusterwideNetworkPolicy";
  public static final String CNP = "CiliumNetworkPolicy";

  public static final String RES = "RequiredEndpointSet";
  public static final String PEC = "PredefinedEndpointCatalog";
}
