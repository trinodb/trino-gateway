package io.trino.gateway.ha.domain.request;

/**
 * QueryHistory Request Body
 *
 * @author Wei Peng
 */
public class QueryHistoryRequest {
  /**
   * page
   */
  private Integer page = 1;
  /**
   * size
   */
  private Integer size = 10;
  /**
   * size
   */
  private String user;

  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }
}
