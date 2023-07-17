package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import static bio.terra.workspace.common.utils.GcpUtils.INSTANCE_SERVICE_ACCOUNT_SCOPES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_DATAPROC_CLUSTER_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_SUBNETWORK_NAME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterInstanceGroupConfig;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterLifecycleConfig;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.model.AcceleratorConfig;
import com.google.api.services.dataproc.model.AutoscalingConfig;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.dataproc.model.ClusterConfig;
import com.google.api.services.dataproc.model.DiskConfig;
import com.google.api.services.dataproc.model.EndpointConfig;
import com.google.api.services.dataproc.model.GceClusterConfig;
import com.google.api.services.dataproc.model.InstanceGroupConfig;
import com.google.api.services.dataproc.model.LifecycleConfig;
import com.google.api.services.dataproc.model.NodeInitializationAction;
import com.google.api.services.dataproc.model.Operation;
import com.google.api.services.dataproc.model.SoftwareConfig;
import com.google.common.annotations.VisibleForTesting;
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
 * A step for creating a Dataproc cluster in the Google cloud.
 *
 * <p>Undo deletes the created cluster.
 */
public class CreateDataprocClusterStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateDataprocClusterStep.class);
  private final ControlledResourceService controlledResourceService;
  private final ControlledDataprocClusterResource resource;
  private final String petEmail;
  private final String workspaceUserFacingId;
  private final CrlService crlService;
  private final CliConfiguration cliConfiguration;

  public CreateDataprocClusterStep(
      ControlledResourceService controlledResourceService,
      ControlledDataprocClusterResource resource,
      String petEmail,
      String workspaceUserFacingId,
      CrlService crlService,
      CliConfiguration cliConfiguration) {
    this.petEmail = petEmail;
    this.resource = resource;
    this.workspaceUserFacingId = workspaceUserFacingId;
    this.controlledResourceService = controlledResourceService;
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

    String region =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_RESOURCE_REGION, String.class);

    ApiGcpDataprocClusterCreationParameters creationParameters =
        flightContext
            .getInputParameters()
            .get(CREATE_DATAPROC_CLUSTER_PARAMETERS, ApiGcpDataprocClusterCreationParameters.class);

    ControlledGcsBucketResource stagingBucket =
        controlledResourceService
            .getControlledResource(resource.getWorkspaceId(), creationParameters.getConfigBucket())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    ControlledGcsBucketResource tempBucket =
        controlledResourceService
            .getControlledResource(resource.getWorkspaceId(), creationParameters.getTempBucket())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    ClusterName clusterName = resource.toClusterName(region);
    Cluster cluster =
        createClusterModel(
            flightContext,
            projectId,
            resource.getClusterId(),
            petEmail,
            workspaceUserFacingId,
            creationParameters,
            stagingBucket.getBucketName(),
            tempBucket.getBucketName(),
            cliConfiguration.getServerName());

    DataprocCow dataprocCow = crlService.getDataprocCow();
    try {
      OperationCow<Operation> creationOperation;
      try {
        creationOperation =
            dataprocCow
                .regionOperations()
                .operationCow(dataprocCow.clusters().create(clusterName, cluster).execute());
      } catch (GoogleJsonResponseException e) {
        // If the cluster already exists, this step must have already run successfully. Otherwise
        // retry.
        if (HttpStatus.CONFLICT.value() == e.getStatusCode()) {
          logger.debug("Dataproc cluster {} already created.", clusterName.formatName());
          return StepResult.getStepResultSuccess();
        } else if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()) {
          // Don't retry bad requests, which won't change. Instead, fail faster.
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
        }
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }

      GcpUtils.pollAndRetry(creationOperation, Duration.ofSeconds(20), Duration.ofMinutes(15));
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static Cluster createClusterModel(
      FlightContext flightContext,
      String projectId,
      String clusterId,
      String serviceAccountEmail,
      String workspaceUserFacingId,
      ApiGcpDataprocClusterCreationParameters creationParameters,
      String stagingBucketName,
      String tempBucketName,
      String cliServer) {
    Cluster cluster = new Cluster();
    setFields(
        clusterId,
        creationParameters,
        stagingBucketName,
        tempBucketName,
        serviceAccountEmail,
        workspaceUserFacingId,
        cliServer,
        cluster);
    setNetworks(cluster, projectId, flightContext.getWorkingMap());
    return cluster;
  }

  @VisibleForTesting
  static Cluster setFields(
      String clusterId,
      ApiGcpDataprocClusterCreationParameters creationParameters,
      String stagingBucketName,
      String tempBucketName,
      String serviceAccountEmail,
      String workspaceUserFacingId,
      String cliServer,
      Cluster cluster) {

    cluster
        .setClusterName(clusterId)
        .setConfig(
            new ClusterConfig()
                .setConfigBucket(stagingBucketName)
                .setTempBucket(tempBucketName)
                .setMasterConfig(
                    getInstanceGroupConfig(creationParameters.getManagerNodeConfig(), false))
                .setWorkerConfig(
                    getInstanceGroupConfig(creationParameters.getPrimaryWorkerConfig(), false))
                .setSecondaryWorkerConfig(
                    creationParameters.getSecondaryWorkerConfig() != null
                        ? getInstanceGroupConfig(
                            creationParameters.getSecondaryWorkerConfig(), true)
                        : getInstanceGroupConfig(
                            creationParameters.getPrimaryWorkerConfig(), false))
                .setGceClusterConfig(
                    new GceClusterConfig()
                        // TODO PF-2878: replace leonardo tag once new dataproc firewall rule is in
                        // place
                        .setTags(List.of("leonardo"))
                        .setServiceAccount(serviceAccountEmail)
                        .setServiceAccountScopes(INSTANCE_SERVICE_ACCOUNT_SCOPES))
                .setAutoscalingConfig(
                    new AutoscalingConfig().setPolicyUri(creationParameters.getAutoscalingPolicy()))
                // Enable dataproc component gateway to set up reverse proxy and web interfaces. See
                // https://cloud.google.com/dataproc/docs/concepts/accessing/dataproc-gateways
                .setEndpointConfig(new EndpointConfig().setEnableHttpPortAccess(true))
                .setSoftwareConfig(
                    new SoftwareConfig()
                        .setProperties(creationParameters.getProperties())
                        .setOptionalComponents(creationParameters.getComponents())));

    // Set initialization script
    // TODO PF-2828: Add WSM default post-startup script
    if (!StringUtils.isEmpty(creationParameters.getInitializationScript())) {
      cluster
          .getConfig()
          .setInitializationActions(
              List.of(
                  new NodeInitializationAction()
                      .setExecutableFile(creationParameters.getInitializationScript())));
    }

    // Configure cluster lifecycle
    ApiGcpDataprocClusterLifecycleConfig lifecycleConfig = creationParameters.getLifecycleConfig();
    if (lifecycleConfig != null) {
      cluster
          .getConfig()
          .setLifecycleConfig(
              new LifecycleConfig().setIdleDeleteTtl(lifecycleConfig.getIdleDeleteTtl()));
      if (lifecycleConfig.getAutoDeleteTime() != null
          && lifecycleConfig.getAutoDeleteTtl() != null) {
        throw new BadRequestException("Cannot specify both autoDeleteTime and autoDeleteTtl");
      }
      if (lifecycleConfig.getAutoDeleteTime() != null) {
        cluster
            .getConfig()
            .setLifecycleConfig(
                new LifecycleConfig()
                    .setAutoDeleteTime(lifecycleConfig.getAutoDeleteTime().toString()));
      }
      if (lifecycleConfig.getAutoDeleteTtl() != null) {
        cluster
            .getConfig()
            .setLifecycleConfig(
                new LifecycleConfig().setAutoDeleteTtl(lifecycleConfig.getAutoDeleteTtl()));
      }
    }

    // Set additional cluster properties
    Map<String, String> properties = new HashMap<>();
    cluster.getConfig().getSoftwareConfig().setProperties(properties);

    // Set metadata on all cluster vm nodes
    Map<String, String> metadata = new HashMap<>();
    Optional.ofNullable(creationParameters.getMetadata()).ifPresent(metadata::putAll);
    addDefaultMetadata(metadata, workspaceUserFacingId, cliServer);
    cluster.getConfig().getGceClusterConfig().setMetadata(metadata);

    return cluster;
  }

  private static InstanceGroupConfig getInstanceGroupConfig(
      ApiGcpDataprocClusterInstanceGroupConfig config, boolean isSecondaryWorkerConfig) {
    InstanceGroupConfig instanceGroupConfig = new InstanceGroupConfig();
    instanceGroupConfig
        .setNumInstances(config.getNumInstances())
        .setMachineTypeUri(config.getMachineType());

    // Set vm node group accelerators
    if (config.getAcceleratorConfig() != null) {
      instanceGroupConfig.setAccelerators(
          List.of(
              new AcceleratorConfig()
                  .setAcceleratorTypeUri(config.getAcceleratorConfig().getType())
                  .setAcceleratorCount(config.getAcceleratorConfig().getCardCount())));
    }

    // Set vm node group disk config
    if (config.getDiskConfig() != null) {
      instanceGroupConfig.setDiskConfig(
          new DiskConfig()
              .setBootDiskType(config.getDiskConfig().getBootDiskType())
              .setBootDiskSizeGb(config.getDiskConfig().getBootDiskSizeGb())
              .setNumLocalSsds(config.getDiskConfig().getNumLocalSsds()));
    }

    // Set vm node group preemptibility
    if (config.getPreemptibility() != null && isSecondaryWorkerConfig) {
      instanceGroupConfig.setPreemptibility(config.getPreemptibility().toString());
    }

    return instanceGroupConfig;
  }

  private static void addDefaultMetadata(
      Map<String, String> metadata, String workspaceUserFacingId, String cliServer) {
    if (metadata.containsKey(GcpResourceConstants.WORKSPACE_ID_METADATA_KEY)
        || metadata.containsKey(GcpResourceConstants.SERVER_ID_METADATA_KEY)) {
      throw new ReservedMetadataKeyException(
          "The metadata keys "
              + GcpResourceConstants.WORKSPACE_ID_METADATA_KEY
              + " and "
              + GcpResourceConstants.SERVER_ID_METADATA_KEY
              + " are reserved for Terra.");
    }
    metadata.put(GcpResourceConstants.WORKSPACE_ID_METADATA_KEY, workspaceUserFacingId);
    if (!StringUtils.isEmpty(cliServer)) {
      metadata.put(GcpResourceConstants.SERVER_ID_METADATA_KEY, cliServer);
    }
  }

  /**
   * We use the same getNetworkStep used by vertex ai notebook and gce instance creation, but we
   * only need the subnetwork name.
   */
  private static void setNetworks(Cluster cluster, String projectId, FlightMap workingMap) {
    String region = workingMap.get(CREATE_RESOURCE_REGION, String.class);
    String subnetworkName = workingMap.get(CREATE_GCE_INSTANCE_SUBNETWORK_NAME, String.class);
    cluster
        .getConfig()
        .getGceClusterConfig()
        .setSubnetworkUri(GcpUtils.toSubnetworkString(projectId, region, subnetworkName));
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String region =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_RESOURCE_REGION, String.class);
    ClusterName clusterName = resource.toClusterName(region);

    DataprocCow dataprocCow = crlService.getDataprocCow();
    try {
      OperationCow<Operation> deletionOperation;
      try {
        deletionOperation =
            dataprocCow
                .regionOperations()
                .operationCow(dataprocCow.clusters().delete(clusterName).execute());
      } catch (GoogleJsonResponseException e) {
        // The Dataproc cluster may have never been created or have already been deleted.
        if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
          logger.debug("No cluster {} to delete.", clusterName.formatName());
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
