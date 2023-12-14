package io.trino.gateway.ha.domain.request;

/**
 * Query ResourceGroups Request Body
 *
 * @author Wei Peng
 */
public class QueryResourceGroupsRequest {
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
