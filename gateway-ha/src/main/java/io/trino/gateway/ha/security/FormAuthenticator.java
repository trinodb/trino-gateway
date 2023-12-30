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
import java.util.Optional;

public class FormAuthenticator implements Authenticator<String, LbPrincipal> {

  private final LbFormAuthManager formAuthManager;
  private final AuthorizationManager authorizationManager;

  public FormAuthenticator(LbFormAuthManager formAuthManager,
                           AuthorizationManager authorizationManager) {
    this.formAuthManager = formAuthManager;
    this.authorizationManager = authorizationManager;
  }

  /**
   * If the idToken is valid and has the right claims, it returns the principal,
   * otherwise is returns an empty optional.
   *
   * @param idToken idToken from authorization server
   * @return an optional principal
   */
  @Override
  public Optional<LbPrincipal> authenticate(String idToken) throws AuthenticationException {
    String userIdField = formAuthManager.getUserIdField();
    return formAuthManager
        .getClaimsFromIdToken(idToken)
        .map(c -> c.get(userIdField))
        .map(Object::toString)
        .map(s -> s.replace("\"", ""))
        .map(sub -> new LbPrincipal(sub, authorizationManager.getPrivileges(sub)));
  }
}
