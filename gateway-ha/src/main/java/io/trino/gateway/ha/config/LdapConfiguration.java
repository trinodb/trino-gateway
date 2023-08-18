package io.trino.gateway.ha.config;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LdapConfiguration {
  private String ldapHost;
  private Integer ldapPort;
  private boolean useTls;
  private boolean useSsl;
  private String ldapAdminBindDn;
  private String ldapUserBaseDn;
  private String ldapUserSearch;
  private String ldapGroupMemberAttribute;
  private String ldapAdminPassword;
  private String ldapTrustStorePath;
  private String ldapTrustStorePassword;

  public static LdapConfiguration load(String path) {
    LdapConfiguration configuration = null;
    try {
      configuration =
          new YamlConfigurationFactory<LdapConfiguration>(LdapConfiguration.class,
              null,
              Jackson.newObjectMapper(), "lb")
              .build(new java.io.File(path));
    } catch (java.io.IOException e) {
      log.error("Error loading configuration file", e);
      throw new RuntimeException(e);
    } catch (ConfigurationException e) {
      log.error("Error loading configuration file", e);
      throw new RuntimeException(e);
    }
    return configuration;
  }
}
