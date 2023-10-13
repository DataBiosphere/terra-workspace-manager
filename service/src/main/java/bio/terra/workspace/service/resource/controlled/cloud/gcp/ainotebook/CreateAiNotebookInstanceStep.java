package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.utils.GcpUtils.INSTANCE_SERVICE_ACCOUNT_SCOPES;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.MAIN_BRANCH;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.PROXY_METADATA_KEY;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.RESOURCE_ID_METADATA_KEY;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource.NOTEBOOK_DISABLE_ROOT_METADATA_KEY;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource.PROXY_MODE_METADATA_KEY;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_LOCATION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_NETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_SUBNETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.app.configuration.external.VersionConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.resource.GcpFlightExceptionUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants;
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
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
      "https://raw.githubusercontent.com/DataBiosphere/terra-workspace-manager/%s/service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/gcp/ainotebook/post-startup.sh";

  /** The Notebook instance metadata value used to set the service account proxy mode. */
  // git secrets gets a false positive if 'service_account' is double quoted.
  private static final String PROXY_MODE_SA_VALUE = "service_" + "account";

  private final Logger logger = LoggerFactory.getLogger(CreateAiNotebookInstanceStep.class);
  private final ControlledAiNotebookInstanceResource resource;
  private final String petEmail;
  private final String workspaceUserFacingId;
  private final CrlService crlService;
  private final CliConfiguration cliConfiguration;
  private final VersionConfiguration versionConfiguration;

  private final FeatureService featureService;

  public CreateAiNotebookInstanceStep(
      ControlledAiNotebookInstanceResource resource,
      String petEmail,
      String workspaceUserFacingId,
      CrlService crlService,
      CliConfiguration cliConfiguration,
      VersionConfiguration versionConfiguration,
      FeatureService featureService) {
    this.petEmail = petEmail;
    this.resource = resource;
    this.workspaceUserFacingId = workspaceUserFacingId;
    this.crlService = crlService;
    this.cliConfiguration = cliConfiguration;
    this.versionConfiguration = versionConfiguration;
    this.featureService = featureService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final GcpCloudContext gcpCloudContext =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.GCP_CLOUD_CONTEXT, GcpCloudContext.class);
    String projectId = gcpCloudContext.getGcpProjectId();
    String requestedLocation =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_LOCATION, String.class);
    InstanceName instanceName = resource.toInstanceName(requestedLocation);

    Optional<String> proxyUrl = featureService.getFeatureValueJson("vwb__wsm_app_proxy_enabled", AppProxyValue.class).map(
        v -> v.proxyUrl);
    Instance instance =
        createInstanceModel(
            flightContext,
            projectId,
            petEmail,
            workspaceUserFacingId,
            resource.getResourceId(),
            cliConfiguration.getServerName(),
            versionConfiguration.getGitHash(),
            proxyUrl);

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
        // Throw bad request exception for malformed parameters
        GcpFlightExceptionUtils.handleGcpBadRequestException(e);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }

      GcpUtils.pollAndRetry(creationOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static Instance createInstanceModel(
      FlightContext flightContext,
      String projectId,
      String serviceAccountEmail,
      String workspaceUserFacingId,
      UUID resourceId,
      String cliServer,
      String gitHash,
      Optional<String> appProxyUrl) {
    Instance instance = new Instance();
    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_NOTEBOOK_PARAMETERS, ApiGcpAiNotebookInstanceCreationParameters.class);
    setFields(
        creationParameters,
        serviceAccountEmail,
        workspaceUserFacingId,
        resourceId,
        cliServer,
        instance,
        gitHash,
        appProxyUrl);
    setNetworks(instance, projectId, flightContext.getWorkingMap());
    return instance;
  }

  @VisibleForTesting
  static Instance setFields(
      ApiGcpAiNotebookInstanceCreationParameters creationParameters,
      String serviceAccountEmail,
      String workspaceUserFacingId,
      UUID resourceId,
      String cliServer,
      Instance instance,
      String gitHash,
      Optional<String> appProxyUrl) {
    var gitHashOrDefault = StringUtils.isEmpty(gitHash) ? MAIN_BRANCH : gitHash;
    instance
        .setPostStartupScript(
            Optional.ofNullable(creationParameters.getPostStartupScript())
                .orElse(String.format(DEFAULT_POST_STARTUP_SCRIPT, gitHashOrDefault)))
        .setMachineType(creationParameters.getMachineType())
        .setInstallGpuDriver(creationParameters.isInstallGpuDriver())
        .setCustomGpuDriverPath(creationParameters.getCustomGpuDriverPath())
        .setBootDiskType(creationParameters.getBootDiskType())
        .setBootDiskSizeGb(creationParameters.getBootDiskSizeGb())
        .setDataDiskType(creationParameters.getDataDiskType())
        .setDataDiskSizeGb(creationParameters.getDataDiskSizeGb());

    instance.setServiceAccount(serviceAccountEmail);
    instance.setServiceAccountScopes(INSTANCE_SERVICE_ACCOUNT_SCOPES);

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
    // Set metadata
    Map<String, String> metadata = new HashMap<>();
    if (containerImageParameters != null) {
      // User needs to run as Jupyter instead of root to have the post-startup.sh run successfully.
      // This is not a TERRA reserved metadata key, so it is overridable. If the creationParameter
      // says otherwise, we will set the value to what the user specified.
      metadata.put(NOTEBOOK_DISABLE_ROOT_METADATA_KEY, "true");
    }
    Optional.ofNullable(creationParameters.getMetadata()).ifPresent(metadata::putAll);
    addDefaultMetadata(metadata, workspaceUserFacingId, cliServer, resourceId, appProxyUrl);
    instance.setMetadata(metadata);
    return instance;
  }

  private static void addDefaultMetadata(
      Map<String, String> metadata, String workspaceUserFacingId, String cliServer, UUID resourceId, Optional<String> proxyUrl) {
    if (metadata.containsKey(GcpResourceConstants.WORKSPACE_ID_METADATA_KEY)
        || metadata.containsKey(GcpResourceConstants.SERVER_ID_METADATA_KEY)
        || metadata.containsKey(PROXY_MODE_METADATA_KEY)
        || metadata.containsKey(RESOURCE_ID_METADATA_KEY)
        || metadata.containsKey(PROXY_METADATA_KEY)) {
      throw new ReservedMetadataKeyException(
          "The metadata keys "
              + GcpResourceConstants.WORKSPACE_ID_METADATA_KEY
              + ", "
              + GcpResourceConstants.SERVER_ID_METADATA_KEY
              + ", "
              + PROXY_MODE_METADATA_KEY
              + ", "
              + RESOURCE_ID_METADATA_KEY
              + ", and"
              + PROXY_METADATA_KEY
              + " are reserved for Terra.");
    }
    metadata.put(GcpResourceConstants.WORKSPACE_ID_METADATA_KEY, workspaceUserFacingId);
    if (!StringUtils.isEmpty(cliServer)) {
      metadata.put(GcpResourceConstants.SERVER_ID_METADATA_KEY, cliServer);
    }
    // Create the AI Notebook instance in the service account proxy mode to control proxy access by
    // means of IAM permissions on the service account.
    // https://cloud.google.com/ai-platform/notebooks/docs/troubleshooting#opening_a_notebook_results_in_a_403_forbidden_error
    metadata.put(PROXY_MODE_METADATA_KEY, PROXY_MODE_SA_VALUE);
    metadata.put(GcpResourceConstants.RESOURCE_ID_METADATA_KEY, resourceId.toString());
    proxyUrl.ifPresent(s -> metadata.put(GcpResourceConstants.PROXY_METADATA_KEY, s));
  }

  private static void setNetworks(Instance instance, String projectId, FlightMap workingMap) {
    String region = workingMap.get(CREATE_RESOURCE_REGION, String.class);
    String networkName = workingMap.get(CREATE_GCE_INSTANCE_NETWORK_NAME, String.class);
    String subnetworkName = workingMap.get(CREATE_GCE_INSTANCE_SUBNETWORK_NAME, String.class);
    instance.setNetwork(GcpUtils.toNetworkString(projectId, networkName));
    instance.setSubnet(GcpUtils.toSubnetworkString(projectId, region, subnetworkName));
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String requestedLocation =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_LOCATION, String.class);
    InstanceName instanceName = resource.toInstanceName(requestedLocation);

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
      GcpUtils.pollAndRetry(deletionOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
    } catch (IOException | RetryException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private record AppProxyValue(String proxyUrl) {}
}
