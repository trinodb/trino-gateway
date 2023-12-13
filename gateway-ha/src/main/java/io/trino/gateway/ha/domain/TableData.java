package io.trino.gateway.ha.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Page Response
 *
 * @author Wei Peng
 */

public class TableData<T> implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * total
   */
  private long total;

  /**
   * data
   */
  private List<T> rows;

  /**
   * @param list  data list
   * @param size  page size
   * @param total data total
   */
  public TableData(List<T> list, long total) {
    this.rows = list;
    this.total = total;
  }

  public static <T> TableData<T> build(List<T> list, long total) {
    return new TableData<>(list, total);
  }

  public TableData() {
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public List<T> getRows() {
    return rows;
  }

  public void setRows(List<T> rows) {
    this.rows = rows;
  }
}
