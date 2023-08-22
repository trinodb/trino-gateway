package io.trino.gateway.baseapp;

import io.dropwizard.Configuration;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppConfiguration extends Configuration {

  // List of Modules with FQCN (Fully Qualified Class Name)
  private List<String> modules;

  // List of ManagedApps with FQCN (Fully Qualified Class Name)
  private List<String> managedApps;
}
