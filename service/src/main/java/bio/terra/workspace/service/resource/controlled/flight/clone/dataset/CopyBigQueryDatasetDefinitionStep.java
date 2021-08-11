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
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CopyBigQueryDatasetDefinitionStep implements Step {

  private final ControlledBigQueryDatasetResource sourceDataset;
  private final ControlledResourceService controlledResourceService;
  private final AuthenticatedUserRequest userRequest;
  private final WorkspaceService workspaceService;

  public CopyBigQueryDatasetDefinitionStep(
      ControlledBigQueryDatasetResource sourceDataset,
      ControlledResourceService controlledResourceService,
      AuthenticatedUserRequest userRequest,
      WorkspaceService workspaceService) {
    this.sourceDataset = sourceDataset;
    this.controlledResourceService = controlledResourceService;
    this.userRequest = userRequest;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final FlightMap workingMap = flightContext.getWorkingMap();
    final CloningInstructions effectiveCloningInstructions =
        inputParameters.get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // TODO: handle cloning a controlled resource with REFERENCE option, PF-812
    if (CloningInstructions.COPY_NOTHING.equals(effectiveCloningInstructions)
        || CloningInstructions.COPY_REFERENCE.equals(effectiveCloningInstructions)) {
      // nothing further to do here or on following steps
      // Build an empty response object
      final ApiClonedControlledGcpBigQueryDataset result =
          new ApiClonedControlledGcpBigQueryDataset()
              .dataset(null)
              .sourceWorkspaceId(sourceDataset.getWorkspaceId())
              .sourceResourceId(sourceDataset.getResourceId())
              .effectiveCloningInstructions(effectiveCloningInstructions.toApiModel());
      FlightUtils.setResponse(flightContext, result, HttpStatus.OK);
      return StepResult.getStepResultSuccess();
    }
    final String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ControlledResourceKeys.RESOURCE_NAME,
            ControlledResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    final String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ControlledResourceKeys.RESOURCE_DESCRIPTION,
            ControlledResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
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
    final ControlledBigQueryDatasetResource destinationResource =
        ControlledBigQueryDatasetResource.builder()
            .accessScope(sourceDataset.getAccessScope())
            .assignedUser(sourceDataset.getAssignedUser().orElse(null))
            .cloningInstructions(sourceDataset.getCloningInstructions())
            .datasetName(datasetName)
            .description(description)
            .managedBy(sourceDataset.getManagedBy())
            .name(resourceName)
            .resourceId(UUID.randomUUID())
            .workspaceId(destinationWorkspaceId)
            .build();

    final ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetName).location(location);
    final List<ControlledResourceIamRole> iamRoles =
        IamRoleUtils.getIamRolesForAccessScope(destinationResource.getAccessScope());

    final ControlledBigQueryDatasetResource clonedResource =
        controlledResourceService.createBigQueryDataset(
            destinationResource, creationParameters, iamRoles, userRequest);

    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedResource);
    final String destinationProjectId =
        workspaceService.getRequiredGcpProject(destinationWorkspaceId);
    final ApiClonedControlledGcpBigQueryDataset apiResult =
        new ApiClonedControlledGcpBigQueryDataset()
            .dataset(clonedResource.toApiResource(destinationProjectId))
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
