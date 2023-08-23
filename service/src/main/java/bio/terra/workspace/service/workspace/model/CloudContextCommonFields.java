package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiOperationState;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;

public record CloudContextCommonFields(
    SpendProfileId spendProfileId,
    WsmResourceState state,
    String flightId,
    ErrorReportException error) {

  public ApiOperationState toApi() {
    var opstate = new ApiOperationState().jobId(flightId).state(state.toApi());
    if (error != null) {
      opstate.errorReport(
          new ApiErrorReport()
              .message(error().getMessage())
              .statusCode(error().getStatusCode().value())
              .causes(error().getCauses()));
    }
    return opstate;
  }
}
