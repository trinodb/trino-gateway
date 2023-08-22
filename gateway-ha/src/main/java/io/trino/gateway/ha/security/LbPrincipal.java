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
