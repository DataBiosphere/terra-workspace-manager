package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Copy a BQ dataset's defining attributes to a new dataset.
 *
 * Preconditions: Accessible source dataset exists
 */
public class CopyBigQueryDatasetDefinitionStep implements Step {

  private final ControlledBigQueryDatasetResource sourceDataset;
  private final ControlledResourceService controlledResourceService;
  private final AuthenticatedUserRequest userRequest;
  private final GcpCloudContextService gcpCloudContextService;

  public CopyBigQueryDatasetDefinitionStep(
      ControlledBigQueryDatasetResource sourceDataset,
      ControlledResourceService controlledResourceService,
      AuthenticatedUserRequest userRequest,
      GcpCloudContextService gcpCloudContextService) {
    this.sourceDataset = sourceDataset;
    this.controlledResourceService = controlledResourceService;
    this.userRequest = userRequest;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final FlightMap workingMap = flightContext.getWorkingMap();
    final CloningInstructions effectiveCloningInstructions =
        inputParameters.get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // TODO: move to the flight
    if (CloningInstructions.COPY_NOTHING.equals(effectiveCloningInstructions)
        || CloningInstructions.COPY_REFERENCE.equals(effectiveCloningInstructions)) {
      // nothing further to do here or on following steps
      // Build an empty response object
      final ApiClonedControlledGcpBigQueryDataset noOpResult =
          new ApiClonedControlledGcpBigQueryDataset()
              .dataset(null)
              .sourceWorkspaceId(sourceDataset.getWorkspaceId())
              .sourceResourceId(sourceDataset.getResourceId())
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
      FlightUtils.setResponse(flightContext, noOpResult, HttpStatus.OK);
      return StepResult.getStepResultSuccess();
    }
    final String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_NAME,
            ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    final String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_DESCRIPTION,
            ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    final String datasetName =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_DATASET_NAME, String.class))
            .orElse(sourceDataset.getDatasetName());
    workingMap.put(ControlledResourceKeys.DESTINATION_DATASET_NAME, datasetName);
    final UUID destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final String location =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ControlledResourceKeys.LOCATION,
            ControlledResourceKeys.LOCATION,
            String.class);
    final String destinationProjectId =
        gcpCloudContextService.getRequiredGcpProject(destinationWorkspaceId);
    final ControlledResourceFields commonFields =
        ControlledResourceFields.builder()
            .accessScope(sourceDataset.getAccessScope())
            .assignedUser(sourceDataset.getAssignedUser().orElse(null))
            .cloningInstructions(sourceDataset.getCloningInstructions())
            .description(description)
            .managedBy(sourceDataset.getManagedBy())
            .name(resourceName)
            .resourceId(UUID.randomUUID())
            .workspaceId(destinationWorkspaceId)
            .build();
    final ControlledBigQueryDatasetResource destinationResource =
        ControlledBigQueryDatasetResource.builder()
            .projectId(destinationProjectId)
            .datasetName(datasetName)
            .common(commonFields)
            .build();

    final ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetName).location(location);
    final ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(destinationResource.getAccessScope());

    final ControlledBigQueryDatasetResource clonedResource =
        controlledResourceService
            .createControlledResourceSync(
                destinationResource, iamRole, userRequest, creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedResource);
    final ApiClonedControlledGcpBigQueryDataset apiResult =
        new ApiClonedControlledGcpBigQueryDataset()
            .dataset(clonedResource.toApiResource())
            .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel())
            .sourceWorkspaceId(sourceDataset.getWorkspaceId())
            .sourceResourceId(sourceDataset.getResourceId());
    workingMap.put(ControlledResourceKeys.CLONE_DEFINITION_RESULT, apiResult);
    if (CloningInstructions.COPY_DEFINITION.equals(effectiveCloningInstructions)
        || CloningInstructions.COPY_RESOURCE.equals(effectiveCloningInstructions)) {
      // Later steps, if any, don't change the success response, since they only affect
      // internal tables and rows in the dataset.
      FlightUtils.setResponse(flightContext, apiResult, HttpStatus.OK);
    }

    return StepResult.getStepResultSuccess();
  }

  // Delete the dataset and its resource entry, i.e. remove the controlled resource
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final ControlledBigQueryDatasetResource clonedDataset =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledBigQueryDatasetResource.class);
    if (clonedDataset != null) {
      controlledResourceService.deleteControlledResourceSync(
          clonedDataset.getWorkspaceId(), clonedDataset.getResourceId(), userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  private String randomDatasetName() {
    return ("terra_wsm_" + UUID.randomUUID().toString().toLowerCase()).replace('-', '_');
  }
}
