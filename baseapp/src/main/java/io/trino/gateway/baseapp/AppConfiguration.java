package io.trino.gateway.baseapp;

import io.dropwizard.core.Configuration;

import java.util.List;

public class AppConfiguration
        extends Configuration
{
    // List of Modules with FQCN (Fully Qualified Class Name)
    private List<String> modules;

    // List of ManagedApps with FQCN (Fully Qualified Class Name)
    private List<String> managedApps;

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
}
