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
package io.trino.gateway.ha.router;

import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;

/**
 * Routing group selector for a YAML rules file. The gateway builds a {@link RulesEngineRoutingGroupSelector} directly;
 * this class is kept because existing MVEL rules can reference {@code FileBasedRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY}.
 */
public class FileBasedRoutingGroupSelector
        extends RulesEngineRoutingGroupSelector
{
    public FileBasedRoutingGroupSelector(String rulesPath, Duration rulesRefreshPeriod, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        super(new FileRoutingRulesStore(rulesPath), rulesRefreshPeriod, requestAnalyzerConfig);
    }
}
