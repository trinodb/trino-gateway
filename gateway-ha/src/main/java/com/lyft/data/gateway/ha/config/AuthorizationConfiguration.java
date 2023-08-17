package com.lyft.data.gateway.ha.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthorizationConfiguration {
  private String admin;
  private String user;
  private String api;
  private String ldapConfigPath;
}
