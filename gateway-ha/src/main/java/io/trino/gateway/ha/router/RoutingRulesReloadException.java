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

/**
 * A routing rule change was saved, but the rule list could not be read back afterwards.
 * Callers must not report the change itself as failed.
 */
public class RoutingRulesReloadException
        extends RuntimeException
{
    public RoutingRulesReloadException(String ruleName, Throwable cause)
    {
        super("Routing rule '%s' was saved, but reloading the rule list failed: %s".formatted(ruleName, cause.getMessage()), cause);
    }
}
