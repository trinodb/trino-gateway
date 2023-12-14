package io.trino.gateway.ha.domain.request;

import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * Selectors Request Body
 *
 * @author Wei Peng
 */
public class SelectorsRequest {
  private String useSchema;
  private ResourceGroupsManager.SelectorsDetail selectorsDetail;

  /**
   * This field is only used for modification
   */
  private ResourceGroupsManager.SelectorsDetail oldSelectorsDetail;


  public String getUseSchema() {
    return useSchema;
  }

  public void setUseSchema(String useSchema) {
    this.useSchema = useSchema;
  }

  public ResourceGroupsManager.SelectorsDetail getSelectorsDetail() {
    return selectorsDetail;
  }

  public void setSelectorsDetail(ResourceGroupsManager.SelectorsDetail selectorsDetail) {
    this.selectorsDetail = selectorsDetail;
  }

  public ResourceGroupsManager.SelectorsDetail getOldSelectorsDetail() {
    return oldSelectorsDetail;
  }

  public void setOldSelectorsDetail(ResourceGroupsManager.SelectorsDetail oldSelectorsDetail) {
    this.oldSelectorsDetail = oldSelectorsDetail;
  }
}
