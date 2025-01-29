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

/**
 * PENDING is for ui/observability purpose and functionally it's unhealthy
 * We should use PENDING when Trino clusters are still spinning up
 * HEALTHY is when health checks report clusters as up
 * UNHEALTHY is when health checks report clusters as down
 * UNKNOWN is when the health checks are not able to determine the status
 */
public enum TrinoStatus
{
    PENDING,
    HEALTHY,
    UNHEALTHY,
    UNKNOWN
}
