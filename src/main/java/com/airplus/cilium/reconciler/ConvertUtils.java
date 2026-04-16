package com.airplus.cilium.reconciler;

import java.util.*;

public class ConvertUtils {

  private static boolean isValidDomain(String address) {
    String re = "^(\\*\\.)?(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63}(?<!-))*\\.[A-Za-z]{2,63}$";
    return address != null && address.matches(re);
  }

  private static boolean isValidCidr(String address) {
    String re = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}(/(3[0-2]|[12]?\\d))?$";
    return address != null && address.matches(re);
  }

  public static List<Map<String, Object>> convertTarget(String address, String port, String protocol) {
    var cidrList = new ArrayList<String>();
    var domainList = new ArrayList<String>();
    var widcardDomainList = new ArrayList<String>();

    String[] splitAdresses = address.split(",");
    Arrays.stream(splitAdresses).forEach(s -> {
      if (isValidCidr(s)) {
        if (s.contains("/")) {
          cidrList.add(s);
        } else {
          cidrList.add(s + "/32");
        }
      }

      if (isValidDomain(s)) {
        if (s.startsWith("*")) {
          widcardDomainList.add(s);
        } else {
          domainList.add(s);
        }
      }
    });

    var fqdns = new ArrayList<Map<String, String>>();

    if (!domainList.isEmpty()) {
      domainList.forEach(d -> fqdns.add(Map.of("matchName", d)));
    }
    if (!widcardDomainList.isEmpty()) {
      widcardDomainList.forEach(d -> fqdns.add(Map.of("matchPattern", d)));
    }

    var toPorts = new ArrayList<Map<String, Object>>();
    String[] splitPorts = port.split(",");
    Arrays.stream(splitPorts).forEach(s -> {
      var portMap = new HashMap<String, Object>();
      portMap.put("protocol", protocol != null ? protocol.toUpperCase() : "ANY");

      if (s.contains("-")) {
        var se = s.split("-");
        portMap.put("port", se[0].trim());
        portMap.put("endPort", Integer.parseInt(se[1].trim()));
      } else {
        portMap.put("port", s.trim());
      }

      toPorts.add(Map.of("ports", List.of(portMap)));
    });

    List<Map<String, Object>> rules = new ArrayList<>();

    if (!cidrList.isEmpty()) {
      Map<String, Object> cidrRule = new HashMap<>();
      cidrRule.put("toCIDR", cidrList);
      if (!toPorts.isEmpty()) {
        cidrRule.put("toPorts", toPorts);
      }
      rules.add(cidrRule);
    }

    if (!fqdns.isEmpty()) {
      Map<String, Object> fqdnRule = new HashMap<>();
      fqdnRule.put("toFQDNs", fqdns);
      if (!toPorts.isEmpty()) {
        fqdnRule.put("toPorts", toPorts);
      }
      rules.add(fqdnRule);
    }

    return rules;
  }
}