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

public class ClusterStatsConfiguration
{
    private boolean useApi;

    public ClusterStatsConfiguration() {}

    public boolean isUseApi()
    {return this.useApi;}

    public void setUseApi(boolean useApi)
    {this.useApi = useApi;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ClusterStatsConfiguration)) {
            return false;
        }
        final ClusterStatsConfiguration other = (ClusterStatsConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isUseApi() != other.isUseApi()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof ClusterStatsConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isUseApi() ? 79 : 97);
        return result;
    }

    @Override
    public String toString()
    {
        return "ClusterStatsConfiguration{" + "useApi=" + useApi + '}';
    }
}
