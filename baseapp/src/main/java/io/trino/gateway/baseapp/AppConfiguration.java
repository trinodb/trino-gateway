/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        final Object thisModules = this.getModules();
        final Object otherModules = other.getModules();
        if (thisModules == null ? otherModules != null : !thisModules.equals(otherModules)) {
            return false;
        }
        final Object thisManagedApps = this.getManagedApps();
        final Object otherManagedApps = other.getManagedApps();
        if (thisManagedApps == null ? otherManagedApps != null : !thisManagedApps.equals(otherManagedApps)) {
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
        final int prime = 59;
        int result = super.hashCode();
        final Object modules = this.getModules();
        result = result * prime + (modules == null ? 43 : modules.hashCode());
        final Object managedApps = this.getManagedApps();
        result = result * prime + (managedApps == null ? 43 : managedApps.hashCode());
        return result;
    }
}
