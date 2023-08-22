package io.trino.gateway.ha.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class OAuthConfiguration {
  private String issuer;
  private String clientId;
  private String clientSecret;
  private String tokenEndpoint;
  private String authorizationEndpoint;
  private String jwkEndpoint;
  private List<String> scopes;
  private String redirectUrl;
  private String userIdField;
}
