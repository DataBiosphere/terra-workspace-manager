package bio.terra.workspace.app.controller;

import bio.terra.policy.model.TpsLocation;
import bio.terra.workspace.generated.controller.PolicyApi;
import bio.terra.workspace.generated.model.ApiRegions;
import bio.terra.workspace.generated.model.ApiWsmPolicyLocation;
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
  public ResponseEntity<ApiWsmPolicyLocation> getRegionInfo(
      String platform, @Nullable String location) {
    TpsLocation tpsRegion = tpsApiDispatch.getLocationInfo(platform, location);

    return new ResponseEntity<>(convertTpsToWsmPolicyRegion(tpsRegion), HttpStatus.OK);
  }

  private static ApiWsmPolicyLocation convertTpsToWsmPolicyRegion(TpsLocation tpsLocation) {
    ApiWsmPolicyLocation wsmPolicyRegion = new ApiWsmPolicyLocation();
    if (tpsLocation.getRegions() != null) {
      ApiRegions regions = new ApiRegions();
      regions.addAll(tpsLocation.getRegions().stream().toList());
      wsmPolicyRegion.regions(regions);
    }
    wsmPolicyRegion.name(tpsLocation.getName()).description(tpsLocation.getDescription());
    if (tpsLocation.getLocations() != null) {
      for (var subLocation : tpsLocation.getLocations()) {
        wsmPolicyRegion.addSublocationsItem(convertTpsToWsmPolicyRegion(subLocation));
      }
    }
    return wsmPolicyRegion;
  }
}
