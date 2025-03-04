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

/**
 * Specifies the sources of routing rules in the Trino Gateway's routing rules engine.
 *
 * <p>By default, requests are routed based on the `X-Trino-Routing-Group` header,
 * or to the default routing group (adhoc) if the header is absent.</p>
 *
 * <p>Routing rules can be defined in two ways:</p>
 * <ul>
 *   <li><strong>FILE:</strong> Rules are specified in a configuration file.</li>
 *   <li><strong>EXTERNAL:</strong> Rules are fetched from an external service via an HTTP POST request.</li>
 * </ul>
 */
public enum RulesType
{
    /**
     * Routing rules defined in a configuration file.
     */
    FILE,

    /**
     * Routing rules obtained from an external service.
     * The service URL can implement dynamic rule changes.
     */
    EXTERNAL,

    /**
     *  Routing rules stored in the database.
     */
    DB
}
