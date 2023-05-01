package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import org.jetbrains.annotations.Nullable;

public record CloudContextCommonFields(
   SpendProfileId spendProfileId,
   WsmResourceState state,
   String flightId,
   ErrorReportException error) {}

