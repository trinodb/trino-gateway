package io.trino.gateway.ha.domain.request;

import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * ResourceGroups Request Body
 *
 * @author Wei Peng
 */
public class ResourceGroupsRequest {
  private String useSchema;
  private ResourceGroupsManager.ResourceGroupsDetail data;

  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public ResourceGroupsManager.ResourceGroupsDetail getData() {
    return data;
  }

  public void setData(ResourceGroupsManager.ResourceGroupsDetail data) {
    this.data = data;
  }
}
