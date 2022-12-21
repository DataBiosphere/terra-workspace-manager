package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.MACHINE_TYPE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_PARAMETERS;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AcceleratorConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstancesSetMachineResourcesRequest;
import com.google.api.services.compute.model.InstancesSetMachineTypeRequest;
import com.google.api.services.compute.model.Scheduling;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** {@link Step} to update cloud attributes (e.g. metadata) for a ai notebook instance. */
public class UpdateAiNotebookAttributesStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final CrlService crlService;
  private final GcpCloudContextService cloudContextService;

  private final Logger logger = LoggerFactory.getLogger(UpdateAiNotebookAttributesStep.class);

  UpdateAiNotebookAttributesStep(
      ControlledAiNotebookInstanceResource resource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.crlService = crlService;
    this.cloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap inputMap = context.getInputParameters();
    final FlightMap workingMap = context.getWorkingMap();
    final ApiGcpAiNotebookUpdateParameters updateParameters =
        inputMap.get(UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);
    if (updateParameters == null) {
      return StepResult.getStepResultSuccess();
    }
    Map<String, String> sanitizedMetadata = new HashMap<>();
    for (var entrySet : updateParameters.getMetadata().entrySet()) {
      if (ControlledAiNotebookInstanceResource.RESERVED_METADATA_KEYS.contains(entrySet.getKey())) {
        logger.error(String.format("Cannot modify terra reserved keys %s", entrySet.getKey()));
        throw new ReservedMetadataKeyException(
            String.format("Cannot modify terra reserved keys %s", entrySet.getKey()));
      }
      sanitizedMetadata.put(entrySet.getKey(), entrySet.getValue());
    }
    String machineType = inputMap.get(MACHINE_TYPE, String.class);
    AcceleratorConfig acceleratorConfig = inputMap.get(ACCELERATOR_CONFIG, AcceleratorConfig.class);
    return updateAiNotebook(
        workingMap,
        sanitizedMetadata,
        cloudContextService.getRequiredGcpProject(resource.getWorkspaceId()),
        machineType,
        acceleratorConfig);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final FlightMap workingMap = context.getWorkingMap();
    final ApiGcpAiNotebookUpdateParameters prevParameters =
        workingMap.get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpAiNotebookUpdateParameters.class);
    String machineType =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_MACHINE_TYPE, String.class);
    AcceleratorConfig acceleratorConfig =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_ACCELERATOR_CONFIG,
            AcceleratorConfig.class);
    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    try {
      var currentMetadata =
          crlService
              .getAIPlatformNotebooksCow()
              .instances()
              .get(resource.toInstanceName(projectId))
              .execute()
              .getMetadata();
      for (var entry : currentMetadata.entrySet()) {
        // reset the new key entry to "" value because the gcp api does not allow deleting
        // metadata item so we can't simply undo the add.
        currentMetadata.put(
            entry.getKey(), prevParameters.getMetadata().getOrDefault(entry.getKey(), ""));
      }
      if (workingMap.get(
              WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATED_MACHINE_CONFIG, String.class)
          == null) {
        machineType = null;
        acceleratorConfig = null;
      }
      return updateAiNotebook(
          workingMap, currentMetadata, projectId, machineType, acceleratorConfig);
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
          || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  private StepResult updateAiNotebook(
      FlightMap workingMap,
      Map<String, String> metadataToUpdate,
      String projectId,
      @Nullable String machineType,
      @Nullable AcceleratorConfig acceleratorConfig) {
    InstanceName instanceName = resource.toInstanceName(projectId);
    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    try {
      notebooks.instances().updateMetadataItems(instanceName, metadataToUpdate).execute();
      updateAiNotebookCpuGpu(
          projectId,
          instanceName.location(),
          instanceName.instanceId(),
          machineType,
          acceleratorConfig);
      workingMap.put(WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATED_MACHINE_CONFIG, "true");
    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
          || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (GeneralSecurityException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private void updateAiNotebookCpuGpu(
      String projectId,
      String location,
      String instanceId,
      String machineType,
      AcceleratorConfig acceleratorConfig)
      throws GeneralSecurityException, IOException, IllegalStateException {
    if (machineType != null
        || (acceleratorConfig != null && acceleratorConfig.getAcceleratorType() != null)) {
      InstanceName instanceName =
          InstanceName.builder()
              .projectId(projectId)
              .location(location)
              .instanceId(instanceId)
              .build();
      var instance = crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();
      if (!instance.getState().equals("STOPPED")) {
        throw new IllegalStateException("Notebook instance has to be stopped before updating.");
      }
    }

    Compute computeService = createComputeService();
    String machineTypeUrl = null, gpuTypeUrl = null;
    if (machineType != null) {
      machineTypeUrl = machineType;
      // GCP requires a URL as machine type and accelerator type. If user input a machine type name,
      // like "n1-standard-2", we augment it into a URL.
      if (machineTypeUrl.lastIndexOf("/") == -1) {
        machineTypeUrl = createBaseUrl(projectId, location) + "/machineTypes/" + machineTypeUrl;
      }
      InstancesSetMachineTypeRequest setMachineTypeRequest = new InstancesSetMachineTypeRequest();
      setMachineTypeRequest.setMachineType(machineTypeUrl);
      computeService
          .instances()
          .setMachineType(projectId, location, instanceId, setMachineTypeRequest)
          .execute();
    }

    if (acceleratorConfig != null && acceleratorConfig.getAcceleratorType() != null) {
      gpuTypeUrl = acceleratorConfig.getAcceleratorType();
      if (gpuTypeUrl.lastIndexOf("/") == -1) {
        gpuTypeUrl =
            createBaseUrl(projectId, location)
                + "/acceleratorTypes/"
                + gpuTypeUrl.toLowerCase().replace("_", "-");
        acceleratorConfig.setAcceleratorType(gpuTypeUrl);
      }

      // Set the scheduling policy so that can update GPU
      Scheduling content = new Scheduling();
      content.setOnHostMaintenance("TERMINATE");
      content.setAutomaticRestart(true);
      computeService.instances().setScheduling(projectId, location, instanceId, content).execute();

      InstancesSetMachineResourcesRequest setMachineResourcesRequest =
          new InstancesSetMachineResourcesRequest();
      setMachineResourcesRequest.setGuestAccelerators(List.of(acceleratorConfig));
      computeService
          .instances()
          .setMachineResources(projectId, location, instanceId, setMachineResourcesRequest)
          .execute();
    }

    // Wait for the instance to finish updating
    int retryCount = 5, retryWaitSeconds = 2;

    for (int i = 0; i < retryCount; i++) {
      Instance instanceInfo =
          computeService.instances().get(projectId, location, instanceId).execute();

      if ((machineType == null || instanceInfo.getMachineType().equals(machineTypeUrl))
          && (acceleratorConfig == null
              || acceleratorConfig.getAcceleratorType() == null
              || instanceInfo
                  .getGuestAccelerators()
                  .get(0)
                  .getAcceleratorType()
                  .equals(gpuTypeUrl))) {
        break;
      }
      try {
        TimeUnit.SECONDS.sleep(retryWaitSeconds);
      } catch (InterruptedException e) {

      }
    }
  }

  /**
   * Directly calling the gcp api to get/update instance, requires the createComputeService, see the
   * example in https://cloud.google.com/compute/docs/reference/rest/v1/instances/get
   */
  public static Compute createComputeService() throws IOException, GeneralSecurityException {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential =
          credential.createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"));
    }

    return new Compute.Builder(httpTransport, jsonFactory, credential)
        .setApplicationName("Google-ComputeSample/0.1")
        .build();
  }

  private String createBaseUrl(String projectId, String location) {
    return "https://www.googleapis.com/compute/v1/projects/" + projectId + "/zones/" + location;
  }
}
