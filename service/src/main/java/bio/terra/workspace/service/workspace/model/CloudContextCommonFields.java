package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.app.controller.shared.ControllerUtils;
import bio.terra.workspace.generated.model.ApiOperationState;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;

public record CloudContextCommonFields(
    SpendProfileId spendProfileId,
    WsmResourceState state,
    String flightId,
    ErrorReportException error) {

  public ApiOperationState toApi() {
    return ControllerUtils.toApiOperationState(flightId, state, error);
  }
}
