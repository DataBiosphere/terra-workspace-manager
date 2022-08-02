package bio.terra.workspace.amalgam.tps;

import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.policy.common.exception.PolicyObjectNotFoundException;
import bio.terra.workspace.generated.controller.TpsApi;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

/**
 * This controller simply extracts the bearer token from the request and calls TpsApiDispatch. That
 * allows the rest of WSM to call TpsApiDispatch using the API classes. If/when we move TPS into its
 * own service, we can rewrite TpsApiDispatch to make an ApiClient and call the appropriate endpoint
 * via the REST API. The rest of WSM will not need to change.
 */
@Controller
public class TpsApiController implements TpsApi {
  private final BearerTokenFactory bearerTokenFactory;
  private final HttpServletRequest request;
  private final TpsApiDispatch tpsApiDispatch;

  @Autowired
  public TpsApiController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      TpsApiDispatch tpsApiDispatch) {
    this.bearerTokenFactory = bearerTokenFactory;
    this.request = request;
    this.tpsApiDispatch = tpsApiDispatch;
  }

  // -- Policy Queries --
  // TODO: PF-1733 Next step is to add group membership constraint

  // -- Policy Attribute Objects --
  @Override
  public ResponseEntity<Void> createPao(ApiTpsPaoCreateRequest body) {
    tpsApiDispatch.createPao(bearerTokenFactory.from(request), body);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deletePao(UUID objectId) {
    tpsApiDispatch.deletePao(bearerTokenFactory.from(request), objectId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiTpsPaoGetResult> getPao(UUID objectId) {
    ApiTpsPaoGetResult result =
        tpsApiDispatch
            .getPao(bearerTokenFactory.from(request), objectId)
            .orElseThrow(
                () -> new PolicyObjectNotFoundException("Policy object not found: " + objectId));
    return new ResponseEntity<>(result, HttpStatus.OK);
  }
}
