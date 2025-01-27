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
package io.trino.gateway.ha.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

public class BackendResponse
        extends ProxyBackendConfiguration
{
    private Integer queued;
    private Integer running;
    private String status;

    @JsonProperty
    public Integer getQueued()
    {
        return queued;
    }

    public void setQueued(Integer queued)
    {
        this.queued = queued;
    }

    @JsonProperty
    public Integer getRunning()
    {
        return running;
    }

    public void setRunning(Integer running)
    {
        this.running = running;
    }

    @JsonProperty
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
