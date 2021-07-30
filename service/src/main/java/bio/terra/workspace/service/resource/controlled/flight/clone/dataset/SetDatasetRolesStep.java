package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import static bio.terra.workspace.common.utils.GcpUtils.pollUntilSuccess;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.util.Strings;
import com.google.api.services.serviceusage.v1.model.BatchEnableServicesRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.datatransfer.v1.ProjectName;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

public class SetDatasetRolesStep implements Step {
  private static final List<String> DESTINATION_DATASET_ROLE_NAMES =
      Stream.of("bigquery.datasets.get", "bigquery.datasets.update", "bigquery.tables.create")
          .collect(Collectors.toList());
  private static final List<String> SOURCE_DATASET_ROLE_NAMES =
      Stream.of("bigquery.datasets.get").collect(Collectors.toList());
  private static final String P4_SA_FORMAT =
      "serviceAccount:service-%s@gcp-sa-bigquerydatatransfer.iam.gserviceaccount.com";
  private final ControlledBigQueryDatasetResource sourceDataset;
  private final WorkspaceService workspaceService;
  private final DatasetCloneRolesComponent datasetCloneRolesComponent;
  private final CrlService crlService;

  public SetDatasetRolesStep(
      ControlledBigQueryDatasetResource sourceDataset,
      WorkspaceService workspaceService,
      DatasetCloneRolesComponent datasetCloneRolesComponent,
      CrlService crlService) {
    this.sourceDataset = sourceDataset;
    this.workspaceService = workspaceService;
    this.datasetCloneRolesComponent = datasetCloneRolesComponent;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();

    final CloningInstructions effectiveCloningInstructions =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // This step is only run for full resource clones
    if (CloningInstructions.COPY_RESOURCE != effectiveCloningInstructions
        && CloningInstructions.COPY_DEFINITION != effectiveCloningInstructions) {
      final ApiClonedControlledGcpBigQueryDataset apiResult =
          new ApiClonedControlledGcpBigQueryDataset()
              .dataset(null)
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
              .sourceWorkspaceId(sourceDataset.getWorkspaceId())
              .sourceResourceId(sourceDataset.getResourceId());
      FlightUtils.setResponse(flightContext, apiResult, HttpStatus.OK);
      return StepResult.getStepResultSuccess();
    }

    // Gather inputs
    final DatasetCloneInputs sourceInputs = getSourceInputs();
    workingMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, sourceInputs);

    final DatasetCloneInputs destinationInputs = getDestinationInputs(flightContext);
    workingMap.put(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, destinationInputs);

    // Get the SA email for the main service account
    final String controlPlaneSAEmail;
    try {
      final GoogleCredentials applicationDefaultCredentials =
          GoogleCredentials.getApplicationDefault();
      final ServiceAccountCredentials saCreds =
          (ServiceAccountCredentials) applicationDefaultCredentials;
      controlPlaneSAEmail = saCreds.getClientEmail();
    } catch (IOException e) {
      throw new RuntimeException("Couldn't get application credentials", e);
    }
    workingMap.put(ControlledResourceKeys.CONTROL_PLANE_SA_EMAIL, controlPlaneSAEmail);
    final AuthenticatedUserRequest userRequest =
        flightContext
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    // put roles on each dataset
    datasetCloneRolesComponent.addDatasetRoles(sourceInputs, controlPlaneSAEmail, userRequest);
    datasetCloneRolesComponent.addDatasetRoles(destinationInputs, controlPlaneSAEmail, userRequest);

    // for testing only - FIXME use RBS if this works
    final ServiceUsageCow serviceUsageCow = crlService.getServiceUsageCow();
    final String parent = ProjectName.of(destinationInputs.getProjectId()).toString();
    final BatchEnableServicesRequest batchEnableServicesRequest =
        new BatchEnableServicesRequest()
            .setServiceIds(ImmutableList.of("bigquerydatatransfer.googleapis.com"));
    try {
      OperationCow<?> operationCow =
          serviceUsageCow
              .operations()
              .operationCow(
                  serviceUsageCow
                      .services()
                      .batchEnable(parent, batchEnableServicesRequest)
                      .execute());
      pollUntilSuccess(operationCow, Duration.ofSeconds(5), Duration.ofMinutes(5));
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final DatasetCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, DatasetCloneInputs.class);
    final DatasetCloneInputs destinationInputs =
        workingMap.get(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, DatasetCloneInputs.class);
    final String saEmail =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_SA_EMAIL, String.class);
    final AuthenticatedUserRequest userRequest =
        flightContext
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    if (!Strings.isNullOrEmpty(saEmail)) {
      if (sourceInputs != null) {
        datasetCloneRolesComponent.removeDatasetRoles(sourceInputs, saEmail, userRequest);
      }
      if (destinationInputs != null) {
        datasetCloneRolesComponent.removeDatasetRoles(destinationInputs, saEmail, userRequest);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  private DatasetCloneInputs getSourceInputs() {
    final String sourceProjectId =
        workspaceService.getRequiredGcpProject(sourceDataset.getWorkspaceId());
    final String sourceDatasetName = sourceDataset.getDatasetName();
    return new DatasetCloneInputs(
        sourceDataset.getWorkspaceId(),
        sourceProjectId,
        sourceDatasetName,
        SOURCE_DATASET_ROLE_NAMES);
  }

  private DatasetCloneInputs getDestinationInputs(FlightContext flightContext) {
    final UUID destinationWorkspaceId =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final String destinationProjectId =
        workspaceService.getRequiredGcpProject(destinationWorkspaceId);
    final String destinationDatasetName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_DATASET_NAME, String.class);
    return new DatasetCloneInputs(
        destinationWorkspaceId,
        destinationProjectId,
        destinationDatasetName,
        DESTINATION_DATASET_ROLE_NAMES);
  }
}
