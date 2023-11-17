package io.trino.gateway.ha.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SelfSignKeyPairConfiguration {
  private String privateKeyRsa;
  private String publicKeyRsa;
}
