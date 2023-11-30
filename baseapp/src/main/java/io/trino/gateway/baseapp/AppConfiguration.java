package io.trino.gateway.baseapp;

import io.dropwizard.core.Configuration;
import java.util.List;

public class AppConfiguration extends Configuration {

  // List of Modules with FQCN (Fully Qualified Class Name)
  private List<String> modules;

  // List of ManagedApps with FQCN (Fully Qualified Class Name)
  private List<String> managedApps;

    public AppConfiguration()
    {
    }

    public List<String> getModules()
    {
        return this.modules;
    }

    public List<String> getManagedApps()
    {
        return this.managedApps;
    }

    public void setModules(List<String> modules)
    {
        this.modules = modules;
    }

    public void setManagedApps(List<String> managedApps)
    {
        this.managedApps = managedApps;
    }

    public String toString()
    {
        return "AppConfiguration(modules=" + this.getModules() + ", managedApps=" + this.getManagedApps() + ")";
    }

    public boolean equals(final Object o)
    {
      if (o == this) {
        return true;
      }
      if (!(o instanceof AppConfiguration)) {
        return false;
      }
        final AppConfiguration other = (AppConfiguration) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
        final Object this$modules = this.getModules();
        final Object other$modules = other.getModules();
      if (this$modules == null ? other$modules != null : !this$modules.equals(other$modules)) {
        return false;
      }
        final Object this$managedApps = this.getManagedApps();
        final Object other$managedApps = other.getManagedApps();
      if (this$managedApps == null ? other$managedApps != null : !this$managedApps.equals(other$managedApps)) {
        return false;
      }
        return true;
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof AppConfiguration;
    }

    public int hashCode()
    {
        final int PRIME = 59;
        int result = super.hashCode();
        final Object $modules = this.getModules();
        result = result * PRIME + ($modules == null ? 43 : $modules.hashCode());
        final Object $managedApps = this.getManagedApps();
        result = result * PRIME + ($managedApps == null ? 43 : $managedApps.hashCode());
        return result;
    }
}
