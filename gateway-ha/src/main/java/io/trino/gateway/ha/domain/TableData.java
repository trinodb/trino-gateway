package io.trino.gateway.ha.domain;

import io.trino.gateway.ha.util.PageUtil;

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
   * total page
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
  public TableData(List<T> list, int size, long total) {
    this.rows = list;
    this.total = PageUtil.totalPage(total, size);
  }

  public static <T> TableData<T> build(List<T> list, int size, long total) {
    return new TableData<>(list, size, total);
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
