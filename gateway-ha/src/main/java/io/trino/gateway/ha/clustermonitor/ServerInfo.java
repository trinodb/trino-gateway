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
package io.trino.gateway.ha.clustermonitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

// based on https://github.com/trinodb/trino/blob/439/client/trino-client/src/main/java/io/trino/client/ServerInfo.java
// without unused fields
public class ServerInfo
{
    private final Boolean starting;

    @JsonCreator
    public ServerInfo(
            @JsonProperty("starting") Boolean starting)
    {
        this.starting = requireNonNull(starting, "starting is null");
    }

    @JsonProperty
    public boolean isStarting()
    {
        return starting;
    }
}
