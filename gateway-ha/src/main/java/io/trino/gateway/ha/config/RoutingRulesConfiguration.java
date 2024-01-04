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

import java.util.Objects;

public class RoutingRulesConfiguration
{
    private boolean rulesEngineEnabled;
    private String rulesConfigPath;

    public RoutingRulesConfiguration() {}

    public boolean isRulesEngineEnabled()
    {
        return this.rulesEngineEnabled;
    }

    public void setRulesEngineEnabled(boolean rulesEngineEnabled)
    {
        this.rulesEngineEnabled = rulesEngineEnabled;
    }

    public String getRulesConfigPath()
    {
        return this.rulesConfigPath;
    }

    public void setRulesConfigPath(String rulesConfigPath)
    {
        this.rulesConfigPath = rulesConfigPath;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoutingRulesConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.isRulesEngineEnabled() != other.isRulesEngineEnabled()) {
            return false;
        }
        final Object rulesConfigPath = this.getRulesConfigPath();
        final Object otherRulesConfigPath = other.getRulesConfigPath();
        return Objects.equals(rulesConfigPath, otherRulesConfigPath);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof RoutingRulesConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        result = result * prime + (this.isRulesEngineEnabled() ? 79 : 97);
        final Object rulesConfigPath = this.getRulesConfigPath();
        result = result * prime + (rulesConfigPath == null ? 43 : rulesConfigPath.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "RoutingRulesConfiguration{" +
                "rulesEngineEnabled=" + rulesEngineEnabled +
                ", rulesConfigPath='" + rulesConfigPath + '\'' +
                '}';
    }
}
