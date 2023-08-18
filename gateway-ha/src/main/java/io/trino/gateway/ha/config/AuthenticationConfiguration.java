package io.trino.gateway.ha.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationConfiguration {
  private String defaultType;
  private OAuthConfiguration oauth;
  private FormAuthConfiguration form;
}
