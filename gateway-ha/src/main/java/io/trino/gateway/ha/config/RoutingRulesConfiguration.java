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

public class RoutingRulesConfiguration
{
    private boolean rulesEngineEnabled;
    private String rulesConfigPath;

    public RoutingRulesConfiguration() {}

    public boolean isRulesEngineEnabled()
    {return this.rulesEngineEnabled;}

    public void setRulesEngineEnabled(boolean rulesEngineEnabled)
    {this.rulesEngineEnabled = rulesEngineEnabled;}

    public String getRulesConfigPath()
    {return this.rulesConfigPath;}

    public void setRulesConfigPath(String rulesConfigPath)
    {this.rulesConfigPath = rulesConfigPath;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoutingRulesConfiguration)) {
            return false;
        }
        final RoutingRulesConfiguration other = (RoutingRulesConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isRulesEngineEnabled() != other.isRulesEngineEnabled()) {
            return false;
        }
        final Object this$rulesConfigPath = this.getRulesConfigPath();
        final Object other$rulesConfigPath = other.getRulesConfigPath();
        if (this$rulesConfigPath == null ? other$rulesConfigPath != null : !this$rulesConfigPath.equals(other$rulesConfigPath)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof RoutingRulesConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isRulesEngineEnabled() ? 79 : 97);
        final Object $rulesConfigPath = this.getRulesConfigPath();
        result = result * PRIME + ($rulesConfigPath == null ? 43 : $rulesConfigPath.hashCode());
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
