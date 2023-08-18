package io.trino.gateway.ha.config;

import lombok.Data;

@Data
public class RoutingRulesConfiguration {
  private boolean rulesEngineEnabled;
  private String rulesConfigPath;
}
