package bio.terra.workspace.service.danglingresource;

import bio.terra.common.logging.LoggingUtils;
import bio.terra.workspace.app.configuration.external.DanglingResourceCleanupConfiguration;
import bio.terra.workspace.db.CronjobDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster.ControlledDataprocClusterResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DanglingResourceCleanupService {
  private static final Logger logger =
      LoggerFactory.getLogger(DanglingResourceCleanupService.class);
  private static final String DANGLING_RESOURCE_CLEANUP_JOB_NAME = "dangling_resource_cleanup_job";

  private final DanglingResourceCleanupConfiguration configuration;
  private final ControlledResourceService controlledResourceService;
  private final JobService jobService;
  private final ResourceDao resourceDao;
  private final CronjobDao cronjobDao;
  private final SamService samService;
  private final CrlService crlService;
  private final ScheduledExecutorService scheduler;

  private final List<WsmResourceType> DANGLING_RESOURCE_TYPES =
      List.of(
          WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE,
          WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE,
          WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);

  @Autowired
  public DanglingResourceCleanupService(
      DanglingResourceCleanupConfiguration configuration,
      ControlledResourceService controlledResourceService,
      JobService jobService,
      ResourceDao resourceDao,
      CronjobDao cronjobDao,
      SamService samService,
      CrlService crlService) {
    this.configuration = configuration;
    this.controlledResourceService = controlledResourceService;
    this.jobService = jobService;
    this.resourceDao = resourceDao;
    this.cronjobDao = cronjobDao;
    this.samService = samService;
    this.crlService = crlService;
    this.scheduler = Executors.newScheduledThreadPool(1);
  }

  @PostConstruct
  public void startStatusChecking() {
    if (configuration.isEnabled()) {
      // Per scheduleAtFixedRate documentation, if a single execution runs longer than the polling
      // interval, subsequent executions may start late but will not concurrently execute.
      scheduler.scheduleAtFixedRate(
          this::cleanupResourcesSuppressExceptions,
          configuration.getStartupWait().toSeconds(),
          configuration.getPollingInterval().toSeconds(),
          TimeUnit.SECONDS);
    }
  }

  /**
   * Run {@code cleanupResources}, suppressing all thrown exceptions. This is helpful as {@code
   * ScheduledExecutorService.scheduleAtFixedRate} will stop running if any execution throws an
   * exception. Suppressing these exceptions ensures we do not stop cleaning up resources if a
   * single run fails.
   */
  public void cleanupResourcesSuppressExceptions() {
    try {
      cleanupResources();
    } catch (Exception e) {
      LoggingUtils.logAlert(
          logger,
          "Unexpected error during danglingResourceCleanup execution, see stacktrace below");
      logger.error("danglingResourceCleanup stacktrace: ", e);
    }
  }

  private void cleanupResources() {
    if (!configuration.isEnabled()) {
      return;
    }
    logger.info("Beginning dangling resource cleanup cronjob");
    // Use a one-second shorter duration here to ensure we don't skip a run by moving slightly too
    // quickly.
    Duration claimTime = configuration.getPollingInterval().minus(Duration.ofSeconds(1));
    // Attempt to claim the latest run of this job to ensure only one pod runs the cleanup job.
    if (!cronjobDao.claimJob(DANGLING_RESOURCE_CLEANUP_JOB_NAME, claimTime)) {
      logger.info("Another pod has executed this job more recently. Ending resource cleanup.");
      return;
    }

    // Read all resource entries that are in the READY state and match the resource type of
    // potential dangling resources.
    List<ControlledResource> resourcesToCheck =
        resourceDao.listReadyResourcesByType(DANGLING_RESOURCE_TYPES);
    logger.info("Checking {} resources to see if they are dangling.", resourcesToCheck.size());

    String wsmSaToken = samService.getWsmServiceAccountToken();
    AuthenticatedUserRequest wsmSaRequest =
        new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));

    // For each resource, check if it exists in the cloud. If it does not, run the cleanup flight to
    // delete its db metadata entry and associated sam resource.
    for (ControlledResource resource : resourcesToCheck) {
      if (!cloudResourceExists(resource, crlService)) {
        launchDanglingResourceCleanupFlight(resource, wsmSaRequest);
      }
    }
  }

  /**
   * Launches a resource deletion flight for a given dangling resource. The controlled resource
   * deletion flight is reused here, but only deletes sam resources and db metadata.
   */
  private void launchDanglingResourceCleanupFlight(
      ControlledResource resource, AuthenticatedUserRequest wsmSaRequest) {

    String jobId = UUID.randomUUID().toString();
    List<WsmResource> resourceToDelete = new ArrayList<>();
    resourceToDelete.add(resource);
    jobService
        .newJob()
        .description(
            "Dangling resource cleanup flight, deleting resource "
                + resource.getResourceId()
                + "of type "
                + resource.getResourceType())
        .jobId(jobId)
        .flightClass(DeleteControlledResourcesFlight.class)
        .userRequest(wsmSaRequest)
        .workspaceId(resource.getWorkspaceId().toString())
        .operationType(OperationType.SYSTEM_CLEANUP)
        // resourceType, resourceName, stewardshipType are set for flight job filtering.
        .resourceType(resource.getResourceType())
        .resourceName(resource.getName())
        .stewardshipType(resource.getStewardshipType())
        .addParameter(ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE, resourceToDelete)
        .submitAndWait();
  }

  /**
   * Utility method to check if a given wsm controlled resource's associated cloud resourced exists.
   */
  private static boolean cloudResourceExists(ControlledResource resource, CrlService crlService) {
    return switch (resource.getResourceType()) {
      case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> aiNotebookInstanceExists(
          resource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE), crlService);
      case CONTROLLED_GCP_GCE_INSTANCE -> gceInstanceExists(
          resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE), crlService);
      case CONTROLLED_GCP_DATAPROC_CLUSTER -> dataprocClusterExists(
          resource.castByEnum(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER), crlService);
      default -> {
        logger.info(
            "Resource {} is not supported for cloud existence check", resource.getResourceType());
        yield false;
      }
    };
  }

  private static boolean aiNotebookInstanceExists(
      ControlledAiNotebookInstanceResource resource, CrlService crlService) {
    try {
      return crlService
              .getAIPlatformNotebooksCow()
              .instances()
              .get(resource.toInstanceName())
              .execute()
          != null;
    } catch (GoogleJsonResponseException e) {
      return HttpStatus.NOT_FOUND.value() != e.getStatusCode();
    } catch (IOException e) {
      // If any other error occurs, assume that the resource may still exist.
      return true;
    }
  }

  private static boolean gceInstanceExists(
      ControlledGceInstanceResource resource, CrlService crlService) {
    try {
      return crlService
              .getCloudComputeCow()
              .instances()
              .get(resource.getProjectId(), resource.getZone(), resource.getInstanceId())
              .execute()
          != null;
    } catch (GoogleJsonResponseException e) {
      return HttpStatus.NOT_FOUND.value() != e.getStatusCode();
    } catch (IOException e) {
      // If any other error occurs, assume that the resource may still exist.
      return true;
    }
  }

  private static boolean dataprocClusterExists(
      ControlledDataprocClusterResource resource, CrlService crlService) {
    try {
      return crlService.getDataprocCow().clusters().get(resource.toClusterName()).execute() != null;
    } catch (GoogleJsonResponseException e) {
      return HttpStatus.NOT_FOUND.value() != e.getStatusCode();
    } catch (IOException e) {
      // If any other error occurs, assume that the resource may still exist.
      return true;
    }
  }
}
