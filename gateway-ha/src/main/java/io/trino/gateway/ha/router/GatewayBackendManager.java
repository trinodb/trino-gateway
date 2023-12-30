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

import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.List;
import java.util.Optional;

public interface GatewayBackendManager {
  List<ProxyBackendConfiguration> getAllBackends();

  List<ProxyBackendConfiguration> getAllActiveBackends();

  List<ProxyBackendConfiguration> getActiveAdhocBackends();

  List<ProxyBackendConfiguration> getActiveBackends(String routingGroup);

  Optional<ProxyBackendConfiguration> getBackendByName(String name);

  ProxyBackendConfiguration addBackend(ProxyBackendConfiguration backend);

  ProxyBackendConfiguration updateBackend(ProxyBackendConfiguration backend);

  void deactivateBackend(String backendName);

  void activateBackend(String backendName);
}
