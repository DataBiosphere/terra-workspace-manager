package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.FlagsApi;
import bio.terra.workspace.generated.model.ApiFeatureState;
import bio.terra.workspace.service.flagsmith.FlagsmithService;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class FlagsApiController implements FlagsApi {
  private final FlagsmithService flagsmithService;
  @Autowired
  public FlagsApiController(FlagsmithService flagsmithService) {
     this.flagsmithService = flagsmithService;
  }

  @Override
  public ResponseEntity<ApiFeatureState> getFeature(String featureName) {
    ApiFeatureState result = new ApiFeatureState().featureName(featureName)
        .isEnabled(flagsmithService.isFeatureEnabled(featureName));
    var value = flagsmithService.getStringValue(featureName);
    result.stringValue(value);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

}
