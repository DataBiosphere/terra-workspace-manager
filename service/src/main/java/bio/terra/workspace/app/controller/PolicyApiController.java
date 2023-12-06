package bio.terra.workspace.app.controller;

import bio.terra.policy.model.TpsLocation;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.generated.controller.PolicyApi;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiRegions;
import bio.terra.workspace.generated.model.ApiWsmPolicyLocation;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

  @WithSpan
  @Override
  public ResponseEntity<ApiWsmPolicyLocation> getLocationInfo(
      ApiCloudPlatform platform, @Nullable String location) {
    TpsLocation tpsLocation =
        Rethrow.onInterrupted(
            () ->
                tpsApiDispatch.getLocationInfo(
                    CloudPlatform.fromApiCloudPlatform(platform), location),
            "getLocationInfo");

    return new ResponseEntity<>(convertTpsToWsmPolicyLocation(tpsLocation), HttpStatus.OK);
  }

  private static ApiWsmPolicyLocation convertTpsToWsmPolicyLocation(TpsLocation tpsLocation) {
    ApiWsmPolicyLocation wsmPolicyLocation = new ApiWsmPolicyLocation();
    if (tpsLocation.getRegions() != null) {
      ApiRegions regions = new ApiRegions();
      regions.addAll(tpsLocation.getRegions().stream().toList());
      wsmPolicyLocation.regions(regions);
    }
    wsmPolicyLocation.name(tpsLocation.getName()).description(tpsLocation.getDescription());
    if (tpsLocation.getLocations() != null) {
      for (var subLocation : tpsLocation.getLocations()) {
        wsmPolicyLocation.addSublocationsItem(convertTpsToWsmPolicyLocation(subLocation));
      }
    }
    return wsmPolicyLocation;
  }
}
