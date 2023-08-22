package io.trino.gateway.ha.config;

import lombok.Data;

@Data
public class SelfSignKeyPairConfiguration {
  private String privateKeyRsa;
  private String publicKeyRsa;
}
