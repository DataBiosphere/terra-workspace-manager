package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_NETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_REGION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_SUBNETWORK_NAME;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.api.services.notebooks.v1.model.ContainerImage;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.api.services.notebooks.v1.model.VmImage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * A step for creating the AI Platform notebook instance in the Google cloud.
 *
 * <p>Undo deletes the created notebook instance.
 */
public class CreateAiNotebookInstanceStep implements Step {

  /** Default post-startup-script when starting a notebook instance. */
  protected static final String DEFAULT_POST_STARTUP_SCRIPT =
      "https://raw.githubusercontent.com/DataBiosphere/terra-workspace-manager/main/service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/gcp/ainotebook/post-startup.sh";
  /** The Notebook instance metadata key used to control proxy mode. */
  private static final String PROXY_MODE_METADATA_KEY = "proxy-mode";
  /** The Notebook instance metadata value used to set the service account proxy mode. */
  // git secrets gets a false positive if 'service_account' is double quoted.
  private static final String PROXY_MODE_SA_VALUE = "service_" + "account";

  /** The Notebook instance metadata key used to set the terra workspace. */
  private static final String WORKSPACE_ID_METADATA_KEY = "terra-workspace-id";
  /**
   * The Notebook instance metadata key used to point the terra CLI at the correct WSM and SAM
   * instances given a CLI specific name.
   */
  private static final String SERVER_ID_METADATA_KEY = "terra-cli-server";

  /**
   * Service account for the notebook instance needs to contain these scopes to interact with SAM.
   */
  private static final List<String> SERVICE_ACCOUNT_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");

  private final Logger logger = LoggerFactory.getLogger(CreateAiNotebookInstanceStep.class);
  private final ControlledAiNotebookInstanceResource resource;
  private final String petEmail;
  private final String userFacingWorkspaceId;
  private final CrlService crlService;
  private final CliConfiguration cliConfiguration;

  public CreateAiNotebookInstanceStep(
      ControlledAiNotebookInstanceResource resource,
      String petEmail,
      String userFacingWorkspaceId,
      CrlService crlService,
      CliConfiguration cliConfiguration) {
    this.petEmail = petEmail;
    this.resource = resource;
    this.userFacingWorkspaceId = userFacingWorkspaceId;
    this.crlService = crlService;
    this.cliConfiguration = cliConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final GcpCloudContext gcpCloudContext =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.GCP_CLOUD_CONTEXT, GcpCloudContext.class);
    String projectId = gcpCloudContext.getGcpProjectId();
    InstanceName instanceName = resource.toInstanceName(projectId);

    Instance instance =
        createInstanceModel(
            flightContext,
            projectId,
            petEmail,
            userFacingWorkspaceId,
            cliConfiguration.getServerName());

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
        } else if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()) {
          // Don't retry bad requests, which won't change. Instead fail faster.
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
        }
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }

      GcpUtils.pollUntilSuccess(creationOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static Instance createInstanceModel(
      FlightContext flightContext,
      String projectId,
      String serviceAccountEmail,
      String userFacingWorkspaceId,
      String cliServer) {
    Instance instance = new Instance();
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_NOTEBOOK_PARAMETERS, ApiGcpAiNotebookInstanceCreationParameters.class);
    setFields(creationParameters, serviceAccountEmail, userFacingWorkspaceId, cliServer, instance);
    setNetworks(instance, projectId, flightContext.getWorkingMap());
    return instance;
  }

  @VisibleForTesting
  static Instance setFields(
      ApiGcpAiNotebookInstanceCreationParameters creationParameters,
      String serviceAccountEmail,
      String userFacingWorkspaceId,
      String cliServer,
      Instance instance) {
    instance
        .setPostStartupScript(
            Optional.ofNullable(creationParameters.getPostStartupScript())
                .orElse(DEFAULT_POST_STARTUP_SCRIPT))
        .setMachineType(creationParameters.getMachineType())
        .setInstallGpuDriver(creationParameters.isInstallGpuDriver())
        .setCustomGpuDriverPath(creationParameters.getCustomGpuDriverPath())
        .setBootDiskType(creationParameters.getBootDiskType())
        .setBootDiskSizeGb(creationParameters.getBootDiskSizeGb())
        .setDataDiskType(creationParameters.getDataDiskType())
        .setDataDiskSizeGb(creationParameters.getDataDiskSizeGb());

    Map<String, String> metadata = new HashMap<>();
    Optional.ofNullable(creationParameters.getMetadata()).ifPresent(metadata::putAll);

    addDefaultMetadata(metadata, userFacingWorkspaceId, cliServer);
    instance.setMetadata(metadata);
    instance.setServiceAccount(serviceAccountEmail);
    instance.setServiceAccountScopes(SERVICE_ACCOUNT_SCOPES);

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

  private static void addDefaultMetadata(
      Map<String, String> metadata, String userFacingWorkspaceId, String cliServer) {
    if (metadata.containsKey(WORKSPACE_ID_METADATA_KEY) ||
        metadata.containsKey(SERVER_ID_METADATA_KEY) ||
        metadata.containsKey(PROXY_MODE_METADATA_KEY)) {
      throw new ReservedMetadataKeyException("The metadata keys " + WORKSPACE_ID_METADATA_KEY + ", " + SERVER_ID_METADATA_KEY + ", and " + PROXY_MODE_METADATA_KEY + " are reserved for Terra.");
    }
    metadata.put(WORKSPACE_ID_METADATA_KEY, userFacingWorkspaceId);
    if (!StringUtils.isEmpty(cliServer)) {
      metadata.put(SERVER_ID_METADATA_KEY, cliServer);
    }
    // Create the AI Notebook instance in the service account proxy mode to control proxy access by
    // means of IAM permissions on the service account.
    // https://cloud.google.com/ai-platform/notebooks/docs/troubleshooting#opening_a_notebook_results_in_a_403_forbidden_error
    metadata.put(PROXY_MODE_METADATA_KEY, PROXY_MODE_SA_VALUE);
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
    final GcpCloudContext gcpCloudContext =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.GCP_CLOUD_CONTEXT, GcpCloudContext.class);
    InstanceName instanceName = resource.toInstanceName(gcpCloudContext.getGcpProjectId());

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
      GcpUtils.pollUntilSuccess(deletionOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
    } catch (IOException | RetryException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
