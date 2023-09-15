package io.trino.gateway.ha.persistence.dao;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

@IdName("cookie")
@Table("cookie_backend_lookup")
public class CookieBackend extends Model {
  public static final String cookie = "cookie";
  public static final String backend = "backend";
  public static final String createdTimestamp = "created_timestamp";

  public static void create(
      CookieBackend model,
      String cookie,
      String backend) {
    model.set(CookieBackend.cookie, cookie);
    model.set(CookieBackend.backend, backend);
    model.set(CookieBackend.createdTimestamp, System.currentTimeMillis());
    model.insert();
  }
}