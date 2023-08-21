package io.trino.gateway.ha.config;

import lombok.Data;

@Data
public class BackendStateConfiguration {
  private String username;
  private String password = "";
  private Boolean ssl = false;
}
