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

public enum QueryLinkType
{
    /**
     * {@code /ui/query.html?<queryId>}, the query page of Trino 482 and earlier.
     */
    LEGACY,

    /**
     * {@code /ui/#/queries/<queryId>}, the query page of the default Web UI in Trino 483 and later.
     */
    PREVIEW,
}
