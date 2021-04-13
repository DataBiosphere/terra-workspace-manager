package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcsAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcsAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcsAiNotebookInstanceVmImage;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.api.services.notebooks.v1.model.ContainerImage;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.api.services.notebooks.v1.model.VmImage;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAiNotebookInstanceStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateAiNotebookInstanceStep.class);
  private final CrlService crlService;
  private final ControlledAiNotebookInstanceResource resource;
  private final WorkspaceService workspaceService;

  public CreateAiNotebookInstanceStep(
      CrlService crlService,
      ControlledAiNotebookInstanceResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    ApiGcsAiNotebookInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_NOTEBOOK_PARAMETERS, ApiGcsAiNotebookInstanceCreationParameters.class);
    Instance instance = createInstance(flightContext, projectId);
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(projectId)
            .location(creationParameters.getLocation())
            .instanceId(creationParameters.getInstanceId())
            .build();

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      // DO NOT SUBMIT previously created?
      // DO NOT SUBMIT check on cloud URI uniqueness?
      OperationCow<Operation> creationOperation =
          notebooks
              .operations()
              .operationCow(notebooks.instances().create(instanceName, instance).execute());
      creationOperation =
          OperationUtils.pollUntilComplete(
              creationOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
      if (creationOperation.getOperation().getError() != null) {
        throw new RetryException(
            String.format(
                "Error creating notebook instance. {}",
                creationOperation.getOperation().getError()));
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static Instance createInstance(FlightContext flightContext, String projectId) {
    Instance instance = new Instance();
    ApiGcsAiNotebookInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_NOTEBOOK_PARAMETERS, ApiGcsAiNotebookInstanceCreationParameters.class);
    setFields(creationParameters, instance);

    String serviceAccountEmail =
        CreateServiceAccountStep.serviceAccountEmail(
            flightContext.getWorkingMap().get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class),
            projectId);
    instance.setServiceAccount(serviceAccountEmail);

    // Create the AI Notebook instance in the service account proxy mode.
    // https://cloud.google.com/ai-platform/notebooks/docs/troubleshooting#opening_a_notebook_results_in_a_403_forbidden_error
    ImmutableMap<String, String> metadata =
        new ImmutableMap.Builder<String, String>().put("proxy-mode", "service_account").build();
    instance.setMetadata(metadata);

    setNetworks(instance, projectId, creationParameters.getLocation());

    return instance;
  }

  private static void setFields(
      ApiGcsAiNotebookInstanceCreationParameters creationParameters, Instance instance) {
    instance
        .setPostStartupScript(creationParameters.getPostStartupScript())
        .setMachineType(creationParameters.getMachineType());
    ApiGcsAiNotebookInstanceVmImage vmImageParameters = creationParameters.getVmImage();
    instance.setVmImage(
        new VmImage()
            .setProject(vmImageParameters.getProjectId())
            .setImageFamily(vmImageParameters.getImageFamily())
            .setImageName(vmImageParameters.getImageName()));
    ApiGcsAiNotebookInstanceContainerImage containerImageParameters =
        creationParameters.getContainerImage();
    instance.setContainerImage(
        new ContainerImage()
            .setRepository(containerImageParameters.getRepository())
            .setTag(containerImageParameters.getTag()));
  }

  private static void setNetworks(Instance instance, String projectId, String location) {
    // 'network' is the name of the VPC network instance created by the Buffer Service.
    // Instead of hard coding this, we could try to look up the name of the network on the project.
    instance.setNetwork("projects/" + projectId + "/global/networks/network");
    // Assume the zone is related to the location like 'us-west1' is to 'us-west1-b'.
    String zone = location.substring(0, location.length() - 2);
    // Like 'network', 'subnetwork' is the name of the subnetwork created by the Buffer Service in
    // each zone.
    instance.setSubnet("projects/" + projectId + "/regions/" + zone + "/subnetworks/subnetwork");
  }

  private static InstanceName createInstanceName(
      String projectId, ApiGcsAiNotebookInstanceCreationParameters creationParameters) {
    return InstanceName.builder()
        .projectId(projectId)
        .location(creationParameters.getLocation())
        .instanceId(creationParameters.getInstanceId())
        .build();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    ApiGcsAiNotebookInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_NOTEBOOK_PARAMETERS, ApiGcsAiNotebookInstanceCreationParameters.class);
    InstanceName instanceName = createInstanceName(projectId, creationParameters);

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      // DO NOT SUBMIT - what if never created or already deleted?
      OperationCow<Operation> deletionOperation =
          notebooks.operations().operationCow(notebooks.instances().delete(instanceName).execute());
      deletionOperation =
          OperationUtils.pollUntilComplete(
              deletionOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
      if (deletionOperation.getOperation().getError() != null) {
        logger.debug(
            "Error deleting notebook instance. {}", deletionOperation.getOperation().getError());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
