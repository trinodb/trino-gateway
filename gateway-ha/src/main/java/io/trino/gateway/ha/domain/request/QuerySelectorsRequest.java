package io.trino.gateway.ha.domain.request;

import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * Query Selectors Request Body
 *
 * @author Wei Peng
 */
public class QuerySelectorsRequest {
  private String useSchema;
  private Long resourceGroupId;

  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public Long getResourceGroupId() {
    return resourceGroupId;
  }

  public void setResourceGroupId(Long resourceGroupId) {
    this.resourceGroupId = resourceGroupId;
  }
}
