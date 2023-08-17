package com.lyft.data.gateway.ha.config;

import lombok.Data;

@Data
public class BackendStateConfiguration {
  private String username;
  private String password = null;
  private Boolean ssl = false;
}
