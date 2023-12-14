package io.trino.gateway.ha.domain.request;

import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * ResourceGroups Request Body
 *
 * @author Wei Peng
 */
public class ResourceGroupsRequest {
  private String useSchema;
  private ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail;

  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public ResourceGroupsManager.ResourceGroupsDetail getResourceGroupsDetail() {
    return resourceGroupsDetail;
  }

  public void setResourceGroupsDetail(ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail) {
    this.resourceGroupsDetail = resourceGroupsDetail;
  }
}
