package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.PolicyApi;
import bio.terra.workspace.generated.model.ApiDataCenterList;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import java.util.List;
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
  public ResponseEntity<ApiDataCenterList> getRegionDataCenters(
      String platform, @Nullable String region) {
    List<String> dataCenters = tpsApiDispatch.listRegionDataCenters(platform, region);
    ApiDataCenterList apiDataCenterList = new ApiDataCenterList();
    apiDataCenterList.addAll(dataCenters);
    return new ResponseEntity<>(apiDataCenterList, HttpStatus.OK);
  }
}
