package io.trino.gateway.ha.domain.request;

/**
 * Query GlobalProperty
 *
 * @author Wei Peng
 */
public class QueryGlobalPropertyRequest {
  private String useSchema;
  private String name;

  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
