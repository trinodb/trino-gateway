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
package io.trino.gateway.ha.persistence.dao;

import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

import java.util.ArrayList;
import java.util.List;

@Table("gateway_backend")
@IdName("name")
@Cached
public class GatewayBackend extends Model {
  private static final String name = "name";
  private static final String routingGroup = "routing_group";
  private static final String backendUrl = "backend_url";
  private static final String externalUrl = "external_url";
  private static final String active = "active";

  public static List<ProxyBackendConfiguration> upcast(List<GatewayBackend> gatewayBackendList) {
    List<ProxyBackendConfiguration> proxyBackendConfigurations = new ArrayList<>();
    for (GatewayBackend model : gatewayBackendList) {
      ProxyBackendConfiguration backendConfig = new ProxyBackendConfiguration();
      backendConfig.setActive(model.getBoolean(active));
      backendConfig.setRoutingGroup(model.getString(routingGroup));
      backendConfig.setProxyTo(model.getString(backendUrl));
      backendConfig.setExternalUrl(model.getString(externalUrl));
      backendConfig.setName(model.getString(name));
      proxyBackendConfigurations.add(backendConfig);
    }
    return proxyBackendConfigurations;
  }

  public static void update(GatewayBackend model, ProxyBackendConfiguration backend) {
    model
        .set(name, backend.getName())
        .set(routingGroup, backend.getRoutingGroup())
        .set(backendUrl, backend.getProxyTo())
        .set(externalUrl, backend.getExternalUrl())
        .set(active, backend.isActive())
        .saveIt();
  }

  public static void create(GatewayBackend model, ProxyBackendConfiguration backend) {
    model
        .create(
            name,
            backend.getName(),
            routingGroup,
            backend.getRoutingGroup(),
            backendUrl,
            backend.getProxyTo(),
            externalUrl,
            backend.getExternalUrl(),
            active,
            backend.isActive())
        .insert();
  }
}
