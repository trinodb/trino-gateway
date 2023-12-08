package io.trino.gateway.ha.domain;

/**
 * REST API Request Body
 *
 * @author Wei Peng
 */
public class RestLoginRequest {
  /**
   * username
   */
  private String username;
  /**
   * password
   */
  private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
