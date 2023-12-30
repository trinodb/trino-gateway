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
package io.trino.gateway.ha.security;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Optional;

public class ApiAuthenticator implements Authenticator<BasicCredentials, LbPrincipal> {
  private final LbFormAuthManager formAuthManager;
  private final AuthorizationManager authorizationManager;

  public ApiAuthenticator(LbFormAuthManager formAuthManager,
                          AuthorizationManager authorizationManager) {
    this.formAuthManager = formAuthManager;
    this.authorizationManager = authorizationManager;
  }

  @Override
  public Optional<LbPrincipal> authenticate(BasicCredentials credentials)
      throws AuthenticationException {
    if (formAuthManager.authenticate(credentials)) {
      return Optional.of(new LbPrincipal(credentials.getUsername(),
          authorizationManager.getPrivileges(credentials.getUsername())));
    }
    return Optional.empty();
  }
}