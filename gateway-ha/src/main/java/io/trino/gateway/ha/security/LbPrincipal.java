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

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

public class LbPrincipal implements Principal {
  private final String name;
  private final Optional<String> memberOf;

  public LbPrincipal(String name, Optional<String> memberOf) {
    this.name = name;
    this.memberOf = memberOf;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LbPrincipal that = (LbPrincipal) o;
    return name.equals(that.name) && memberOf.equals(that.memberOf);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, memberOf);
  }

  @Override
  public String getName() {
    return name;
  }

  public Optional<String> getMemberOf() {
    return this.memberOf;
  }
}
