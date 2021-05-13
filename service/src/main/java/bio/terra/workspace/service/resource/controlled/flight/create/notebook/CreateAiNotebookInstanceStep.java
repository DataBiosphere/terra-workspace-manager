package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_NETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_REGION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SUBNETWORK_NAME;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.api.services.notebooks.v1.model.ContainerImage;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.api.services.notebooks.v1.model.VmImage;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * A step for creating the AI Platform notebook instance in the Google cloud.
 *
 * <p>Undo deletes the created notebook instance.
 */
public class CreateAiNotebookInstanceStep implements Step {
  /** The Notebook instance metadata key used to control proxy mode. */
  private static final String PROXY_MODE_METADATA_KEY = "proxy-mode";
  /** The Notebook instance metadata value used to set the service account proxy mode. */
  // git secrets gets a false positive if 'service_account' is double quoted.
  private static final String PROXY_MODE_SA_VALUE = "service_" + "account";

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
    InstanceName instanceName = resource.toInstanceName(projectId);
    Instance instance = createInstanceModel(flightContext, projectId);

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      OperationCow<Operation> creationOperation;
      try {
        creationOperation =
            notebooks
                .operations()
                .operationCow(notebooks.instances().create(instanceName, instance).execute());
      } catch (GoogleJsonResponseException e) {
        // If the instance already exists, this step must have already run successfully. Otherwise
        // retry.
        if (HttpStatus.CONFLICT.value() == e.getStatusCode()) {
          logger.debug("Notebook instance {} already created.", instanceName.formatName());
          return StepResult.getStepResultSuccess();
        }
        if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()) {
          // Don't retry bad requests, which won't change. Instead fail faster.
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
        }
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }

      creationOperation =
          OperationUtils.pollUntilComplete(
              creationOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
      if (creationOperation.getOperation().getError() != null) {
        throw new RetryException(
            String.format(
                "Error creating notebook instance %s. %s",
                instanceName.formatName(), creationOperation.getOperation().getError()));
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static Instance createInstanceModel(FlightContext flightContext, String projectId) {
    Instance instance = new Instance();
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_NOTEBOOK_PARAMETERS, ApiGcpAiNotebookInstanceCreationParameters.class);
    String serviceAccountEmail =
        ServiceAccountName.emailFromAccountId(
            flightContext.getWorkingMap().get(CREATE_NOTEBOOK_SERVICE_ACCOUNT_ID, String.class),
            projectId);
    setFields(creationParameters, serviceAccountEmail, instance);
    setNetworks(instance, projectId, flightContext.getWorkingMap());
    return instance;
  }

  @VisibleForTesting
  static Instance setFields(
      ApiGcpAiNotebookInstanceCreationParameters creationParameters,
      String serviceAccountEmail,
      Instance instance) {
    instance
        .setPostStartupScript(creationParameters.getPostStartupScript())
        .setMachineType(creationParameters.getMachineType())
        .setInstallGpuDriver(creationParameters.isInstallGpuDriver())
        .setCustomGpuDriverPath(creationParameters.getCustomGpuDriverPath())
        .setBootDiskType(creationParameters.getBootDiskType())
        .setBootDiskSizeGb(creationParameters.getBootDiskSizeGb())
        .setDataDiskType(creationParameters.getDataDiskType())
        .setDataDiskSizeGb(creationParameters.getDataDiskSizeGb());

    Map<String, String> metadata = new HashMap<>();
    Optional.ofNullable(creationParameters.getMetadata()).ifPresent(metadata::putAll);
    // Create the AI Notebook instance in the service account proxy mode to control proxy access by
    // means of IAM permissions on the service account.
    // https://cloud.google.com/ai-platform/notebooks/docs/troubleshooting#opening_a_notebook_results_in_a_403_forbidden_error
    if (metadata.put(PROXY_MODE_METADATA_KEY, PROXY_MODE_SA_VALUE) != null) {
      throw new BadRequestException("proxy-mode metadata is reserved for Terra.");
    }
    instance.setMetadata(metadata);
    instance.setServiceAccount(serviceAccountEmail);

    ApiGcpAiNotebookInstanceAcceleratorConfig acceleratorConfig =
        creationParameters.getAcceleratorConfig();
    if (acceleratorConfig != null) {
      instance.setAcceleratorConfig(
          new AcceleratorConfig()
              .setType(acceleratorConfig.getType())
              .setCoreCount(acceleratorConfig.getCoreCount()));
    }
    ApiGcpAiNotebookInstanceVmImage vmImageParameters = creationParameters.getVmImage();
    if (vmImageParameters != null) {
      instance.setVmImage(
          new VmImage()
              .setProject(vmImageParameters.getProjectId())
              .setImageFamily(vmImageParameters.getImageFamily())
              .setImageName(vmImageParameters.getImageName()));
    }
    ApiGcpAiNotebookInstanceContainerImage containerImageParameters =
        creationParameters.getContainerImage();
    if (containerImageParameters != null) {
      instance.setContainerImage(
          new ContainerImage()
              .setRepository(containerImageParameters.getRepository())
              .setTag(containerImageParameters.getTag()));
    }
    return instance;
  }

  private static void setNetworks(Instance instance, String projectId, FlightMap workingMap) {
    String region = workingMap.get(CREATE_NOTEBOOK_REGION, String.class);
    String networkName = workingMap.get(CREATE_NOTEBOOK_NETWORK_NAME, String.class);
    String subnetworkName = workingMap.get(CREATE_NOTEBOOK_SUBNETWORK_NAME, String.class);
    instance.setNetwork("projects/" + projectId + "/global/networks/" + networkName);
    instance.setSubnet(
        "projects/" + projectId + "/regions/" + region + "/subnetworks/" + subnetworkName);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    InstanceName instanceName = resource.toInstanceName(projectId);

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      OperationCow<Operation> deletionOperation;
      try {
        deletionOperation =
            notebooks
                .operations()
                .operationCow(notebooks.instances().delete(instanceName).execute());
      } catch (GoogleJsonResponseException e) {
        // The AI notebook instance may never have been created or have already been deleted.
        if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
          logger.debug("No notebook instance {} to delete.", instanceName.formatName());
          return StepResult.getStepResultSuccess();
        }
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
      deletionOperation =
          OperationUtils.pollUntilComplete(
              deletionOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
      if (deletionOperation.getOperation().getError() != null) {
        logger.debug(
            "Error deleting notebook instance {}. {}",
            instanceName.formatName(),
            deletionOperation.getOperation().getError());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
