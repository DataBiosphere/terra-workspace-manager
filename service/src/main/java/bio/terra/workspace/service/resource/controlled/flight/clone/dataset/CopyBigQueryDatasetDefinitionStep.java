package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledBigQueryDataset;

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
 * <p>Preconditions: Accessible source dataset exists. Cloning instructions are COPY_DEFINITION or
 * COPY_RESOURCE.
 *
 * <p>Post conditions: Empty destination dataset exists. Flight response is marked successful if
 * appropriate.
 */
public class CopyBigQueryDatasetDefinitionStep implements Step {

  private final ControlledBigQueryDatasetResource sourceDataset;
  private final ControlledResourceService controlledResourceService;
  private final AuthenticatedUserRequest userRequest;
  private final GcpCloudContextService gcpCloudContextService;
  private final CloningInstructions resolvedCloningInstructions;

  public CopyBigQueryDatasetDefinitionStep(
      ControlledBigQueryDatasetResource sourceDataset,
      ControlledResourceService controlledResourceService,
      AuthenticatedUserRequest userRequest,
      GcpCloudContextService gcpCloudContextService,
      CloningInstructions resolvedCloningInstructions) {
    this.sourceDataset = sourceDataset;
    this.controlledResourceService = controlledResourceService;
    this.userRequest = userRequest;
    this.gcpCloudContextService = gcpCloudContextService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        ControlledResourceKeys.DESTINATION_FOLDER_ID);
    FlightMap workingMap = flightContext.getWorkingMap();
    String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_NAME,
            ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_DESCRIPTION,
            ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    var datasetName =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_DATASET_NAME, String.class))
            .orElse(sourceDataset.getDatasetName());
    workingMap.put(ControlledResourceKeys.DESTINATION_DATASET_NAME, datasetName);
    var destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    String location =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ControlledResourceKeys.LOCATION,
            ControlledResourceKeys.LOCATION,
            String.class);
    String destinationProjectId =
        gcpCloudContextService.getRequiredGcpProject(destinationWorkspaceId);
    var destinationResourceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    UUID destinationFolderId =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_FOLDER_ID, String.class))
            .map(UUID::fromString)
            .orElse(null);
    ControlledBigQueryDatasetResource destinationResource =
        buildDestinationControlledBigQueryDataset(
            sourceDataset,
            destinationWorkspaceId,
            destinationResourceId,
            destinationFolderId,
            resourceName,
            description,
            datasetName,
            destinationProjectId);

    var creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetName).location(location);
    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(destinationResource.getAccessScope());

    ControlledBigQueryDatasetResource clonedResource =
        controlledResourceService
            .createControlledResourceSync(
                destinationResource, iamRole, userRequest, creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedResource);
    var apiResult =
        new ApiClonedControlledGcpBigQueryDataset()
            .dataset(clonedResource.toApiResource())
            .effectiveCloningInstructions(resolvedCloningInstructions.toApiModel())
            .sourceWorkspaceId(sourceDataset.getWorkspaceId())
            .sourceResourceId(sourceDataset.getResourceId());
    workingMap.put(ControlledResourceKeys.CLONE_DEFINITION_RESULT, apiResult);

    // Later steps, if any, don't change the success response, since they only affect
    // internal tables and rows in the dataset.
    FlightUtils.setResponse(flightContext, apiResult, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  // Delete the dataset and its resource entry, i.e. remove the controlled resource
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    ControlledBigQueryDatasetResource clonedDataset =
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
}
