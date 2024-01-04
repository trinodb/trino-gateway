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
}
