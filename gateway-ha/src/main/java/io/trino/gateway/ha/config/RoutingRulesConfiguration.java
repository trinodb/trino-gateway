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
package io.trino.gateway.ha.config;

import io.airlift.units.Duration;

import static java.util.concurrent.TimeUnit.MINUTES;

public class RoutingRulesConfiguration
{
    private boolean rulesEngineEnabled;
    private RulesType rulesType = RulesType.FILE;
    private String rulesConfigPath;
    private RulesExternalConfiguration rulesExternalConfiguration;

    private Duration rulesRefreshPeriod = new Duration(1, MINUTES);

    public RoutingRulesConfiguration() {}

    public boolean isRulesEngineEnabled()
    {
        return this.rulesEngineEnabled;
    }

    public void setRulesEngineEnabled(boolean rulesEngineEnabled)
    {
        this.rulesEngineEnabled = rulesEngineEnabled;
    }

    public RulesType getRulesType()
    {
        return rulesType;
    }

    public void setRulesType(RulesType rulesType)
    {
        this.rulesType = rulesType;
    }

    public String getRulesConfigPath()
    {
        return this.rulesConfigPath;
    }

    public void setRulesConfigPath(String rulesConfigPath)
    {
        this.rulesConfigPath = rulesConfigPath;
    }

    public RulesExternalConfiguration getRulesExternalConfiguration()
    {
        return this.rulesExternalConfiguration;
    }

    public void setRulesExternalConfiguration(RulesExternalConfiguration rulesExternalConfiguration)
    {
        this.rulesExternalConfiguration = rulesExternalConfiguration;
    }

    public Duration getRulesRefreshPeriod()
    {
        return rulesRefreshPeriod;
    }

    public void setRulesRefreshPeriod(Duration rulesRefreshPeriod)
    {
        this.rulesRefreshPeriod = rulesRefreshPeriod;
    }
}
