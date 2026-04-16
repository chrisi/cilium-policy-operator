package com.airplus.cilium.reconciler;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ConvertUtilsTest {

  @Test
  void convertAddress() {
    Map<String, Object> obj = ConvertUtils.convertTarget("10.10.10.10,20.30.40.0/22,*.google.de,www.google.de", "44,23-24", "ANY");
  }
}