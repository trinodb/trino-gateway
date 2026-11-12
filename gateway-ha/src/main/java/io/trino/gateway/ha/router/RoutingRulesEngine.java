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

import java.util.Map;
import java.util.concurrent.Future;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * The routing rules in use on this pod, for managing them. Routing by header or by an EXTERNAL service uses {@link #NO_OP}.
 */
public interface RoutingRulesEngine
{
    RoutingRulesEngine NO_OP = new RoutingRulesEngine()
    {
        @Override
        public Future<?> triggerRefresh()
        {
            return completedFuture(null);
        }

        @Override
        public Map<String, String> getRuleEvaluationErrors()
        {
            return Map.of();
        }
    };

    /**
     * Reloads the rules from the store in the background.
     *
     * @return a Future that completes when the reload finishes
     */
    Future<?> triggerRefresh();

    /**
     * Rules that failed when evaluated on this pod since they last changed, mapped to the first error message.
     */
    Map<String, String> getRuleEvaluationErrors();
}
