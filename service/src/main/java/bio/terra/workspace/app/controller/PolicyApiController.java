package bio.terra.workspace.app.controller;

import bio.terra.policy.model.TpsRegion;
import bio.terra.workspace.generated.controller.PolicyApi;
import bio.terra.workspace.generated.model.ApiDataCenterList;
import bio.terra.workspace.generated.model.ApiWsmPolicyRegion;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class PolicyApiController implements PolicyApi {
  private final TpsApiDispatch tpsApiDispatch;

  public PolicyApiController(TpsApiDispatch tpsApiDispatch) {
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public ResponseEntity<ApiWsmPolicyRegion> getRegionInfo(
      String platform, @Nullable String location) {
    TpsRegion tpsRegion = tpsApiDispatch.getRegionInfo(platform, location);

    return new ResponseEntity<>(convertTpsToWsmPolicyRegion(tpsRegion), HttpStatus.OK);
  }

  private static ApiWsmPolicyRegion convertTpsToWsmPolicyRegion(TpsRegion tpsRegion) {
    ApiWsmPolicyRegion wsmPolicyRegion = new ApiWsmPolicyRegion();
    if (tpsRegion.getDatacenters() != null) {
      ApiDataCenterList datacenterList = new ApiDataCenterList();
      datacenterList.addAll(tpsRegion.getDatacenters().stream().toList());
      wsmPolicyRegion.datacenters(datacenterList);
    }
    wsmPolicyRegion.name(tpsRegion.getName()).description(tpsRegion.getDescription());
    if (tpsRegion.getRegions() != null) {
      for (var subRegion : tpsRegion.getRegions()) {
        wsmPolicyRegion.addRegionsItem(convertTpsToWsmPolicyRegion(subRegion));
      }
    }
    return wsmPolicyRegion;
  }
}
