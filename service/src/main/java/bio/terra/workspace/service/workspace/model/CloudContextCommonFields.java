package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;

public record CloudContextCommonFields(
    SpendProfileId spendProfileId,
    WsmResourceState state,
    String flightId,
    ErrorReportException error) {}
