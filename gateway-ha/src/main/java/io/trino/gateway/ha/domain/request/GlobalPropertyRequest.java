package io.trino.gateway.ha.domain.request;

import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * GlobalProperty
 *
 * @author Wei Peng
 */
public class GlobalPropertyRequest {
  private String useSchema;
  private ResourceGroupsManager.GlobalPropertiesDetail data;

  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public ResourceGroupsManager.GlobalPropertiesDetail getData() {
    return data;
  }

  public void setData(ResourceGroupsManager.GlobalPropertiesDetail data) {
    this.data = data;
  }
}
