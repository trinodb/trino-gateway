package com.lyft.data.gateway.ha.config;

import lombok.Data;

@Data
public class SelfSignKeyPairConfiguration {
  private String privateKeyRsa;
  private String publicKeyRsa;
}
