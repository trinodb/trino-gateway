package io.trino.gateway.ha.domain.request;

import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * Selectors Request Body
 *
 * @author Wei Peng
 */
public class SelectorsRequest {
  private String useSchema;
  private ResourceGroupsManager.SelectorsDetail data;

  /**
   * This field is only used for modification
   */
  private ResourceGroupsManager.SelectorsDetail oldData;


  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public ResourceGroupsManager.SelectorsDetail getData() {
    return data;
  }

  public void setData(ResourceGroupsManager.SelectorsDetail data) {
    this.data = data;
  }

  public ResourceGroupsManager.SelectorsDetail getOldData() {
    return oldData;
  }

  public void setOldData(ResourceGroupsManager.SelectorsDetail oldData) {
    this.oldData = oldData;
  }
}
