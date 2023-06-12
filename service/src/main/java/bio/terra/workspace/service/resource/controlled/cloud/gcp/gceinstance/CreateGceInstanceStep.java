package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource.SERVER_ID_METADATA_KEY;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource.WORKSPACE_ID_METADATA_KEY;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_NETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_SUBNETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_ZONE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
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
import bio.terra.workspace.generated.model.ApiGcpGceInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceGuestAccelerator;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.AcceleratorConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * A step for creating the GCE instance in the Google cloud.
 *
 * <p>Undo deletes the created instance.
 */
public class CreateGceInstanceStep implements Step {
  /** Service account for the instance needs to contain these scopes to interact with SAM. */
  private static final List<String> SERVICE_ACCOUNT_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");

  private final Logger logger = LoggerFactory.getLogger(CreateGceInstanceStep.class);
  private final ControlledGceInstanceResource resource;
  private final String petEmail;
  private final String workspaceUserFacingId;
  private final CrlService crlService;
  private final CliConfiguration cliConfiguration;
  private final VersionConfiguration versionConfiguration;

  public CreateGceInstanceStep(
      ControlledGceInstanceResource resource,
      String petEmail,
      String workspaceUserFacingId,
      CrlService crlService,
      CliConfiguration cliConfiguration,
      VersionConfiguration versionConfiguration) {
    this.petEmail = petEmail;
    this.resource = resource;
    this.workspaceUserFacingId = workspaceUserFacingId;
    this.crlService = crlService;
    this.cliConfiguration = cliConfiguration;
    this.versionConfiguration = versionConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final GcpCloudContext gcpCloudContext =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.GCP_CLOUD_CONTEXT, GcpCloudContext.class);
    String projectId = gcpCloudContext.getGcpProjectId();
    String zone =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_ZONE, String.class);

    Instance instance =
        createInstanceModel(
            flightContext,
            resource.getInstanceId(),
            projectId,
            petEmail,
            workspaceUserFacingId,
            cliConfiguration.getServerName(),
            versionConfiguration.getGitHash());

    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    try {
      OperationCow<Operation> creationOperation;
      try {
        creationOperation =
            cloudComputeCow
                .zoneOperations()
                .operationCow(
                    projectId,
                    zone,
                    cloudComputeCow.instances().insert(projectId, zone, instance).execute());
      } catch (GoogleJsonResponseException e) {
        // If the instance already exists, this step must have already run successfully. Otherwise
        // retry.
        if (HttpStatus.CONFLICT.value() == e.getStatusCode()) {
          logger.debug(
              "Compute instance projects/{}/zones/{}/instances/{} already created.",
              projectId,
              zone,
              resource.getInstanceId());
          return StepResult.getStepResultSuccess();
        } else if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()) {
          // Don't retry bad requests, which won't change. Instead fail faster.
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
        }
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
      String instanceId,
      String projectId,
      String serviceAccountEmail,
      String workspaceUserFacingId,
      String cliServer,
      String gitHash) {
    Instance instance = new Instance();
    ApiGcpGceInstanceCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_GCE_INSTANCE_PARAMETERS, ApiGcpGceInstanceCreationParameters.class);
    setFields(
        creationParameters,
        instanceId,
        serviceAccountEmail,
        workspaceUserFacingId,
        cliServer,
        instance,
        gitHash);
    setNetworks(instance, projectId, flightContext.getWorkingMap());
    return instance;
  }

  @VisibleForTesting
  static Instance setFields(
      ApiGcpGceInstanceCreationParameters creationParameters,
      String instanceId,
      String serviceAccountEmail,
      String workspaceUserFacingId,
      String cliServer,
      Instance instance,
      String gitHash) {
    instance.setName(instanceId).setMachineType(creationParameters.getMachineType());

    instance.setDisks(
        List.of(
            new AttachedDisk()
                .setBoot(true)
                .setAutoDelete(true)
                .setInitializeParams(
                    new AttachedDiskInitializeParams()
                        .setSourceImage(creationParameters.getVmImage())
                        .setDiskSizeGb(Long.valueOf(100)))));

    instance.setServiceAccounts(
        List.of(
            new ServiceAccount().setEmail(serviceAccountEmail).setScopes(SERVICE_ACCOUNT_SCOPES)));

    List<ApiGcpGceInstanceGuestAccelerator> guestAccelerators =
        creationParameters.getGuestAccelerators();
    if (guestAccelerators != null) {
      instance.setGuestAccelerators(
          guestAccelerators.stream()
              .map(
                  accelerator ->
                      new AcceleratorConfig()
                          .setAcceleratorType(accelerator.getType())
                          .setAcceleratorCount(accelerator.getCardCount()))
              .collect(Collectors.toList()));
    }
    // Set metadata
    Map<String, String> metadata = new HashMap<>();
    Optional.ofNullable(creationParameters.getMetadata()).ifPresent(metadata::putAll);
    addDefaultMetadata(metadata, workspaceUserFacingId, cliServer);
    instance.setMetadata(
        new Metadata()
            .setItems(
                metadata.entrySet().stream()
                    .map(i -> new Items().setKey(i.getKey()).setValue(i.getValue()))
                    .collect(Collectors.toList())));
    return instance;
  }

  private static void addDefaultMetadata(
      Map<String, String> metadata, String workspaceUserFacingId, String cliServer) {
    if (metadata.containsKey(WORKSPACE_ID_METADATA_KEY)
        || metadata.containsKey(SERVER_ID_METADATA_KEY)) {
      throw new ReservedMetadataKeyException(
          "The metadata keys "
              + WORKSPACE_ID_METADATA_KEY
              + " and "
              + SERVER_ID_METADATA_KEY
              + " are reserved for Terra.");
    }
    metadata.put(WORKSPACE_ID_METADATA_KEY, workspaceUserFacingId);
    if (!StringUtils.isEmpty(cliServer)) {
      metadata.put(SERVER_ID_METADATA_KEY, cliServer);
    }
  }

  private static void setNetworks(Instance instance, String projectId, FlightMap workingMap) {
    String region = workingMap.get(CREATE_RESOURCE_REGION, String.class);
    String networkName = workingMap.get(CREATE_GCE_INSTANCE_NETWORK_NAME, String.class);
    String subnetworkName = workingMap.get(CREATE_GCE_INSTANCE_SUBNETWORK_NAME, String.class);
    instance.setNetworkInterfaces(
        List.of(
            new NetworkInterface()
                .setNetwork("projects/" + projectId + "/global/networks/" + networkName)
                .setSubnetwork(
                    "projects/"
                        + projectId
                        + "/regions/"
                        + region
                        + "/subnetworks/"
                        + subnetworkName)));
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final GcpCloudContext gcpCloudContext =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.GCP_CLOUD_CONTEXT, GcpCloudContext.class);
    String projectId = gcpCloudContext.getGcpProjectId();
    String zone =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_ZONE, String.class);

    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    try {
      OperationCow<Operation> deletionOperation;
      try {
        deletionOperation =
            cloudComputeCow
                .zoneOperations()
                .operationCow(
                    projectId,
                    zone,
                    cloudComputeCow
                        .instances()
                        .delete(projectId, zone, resource.getInstanceId())
                        .execute());
      } catch (GoogleJsonResponseException e) {
        // The instance may never have been created or have already been deleted.
        if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
          logger.debug(
              "No compute instance projects/{}/zones/{}/instances/{} to delete.",
              projectId,
              zone,
              resource.getInstanceId());
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
}
