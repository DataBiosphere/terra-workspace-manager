package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.crl.CrlService.getBigQueryDataset;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_WITHOUT_REGION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_REGION_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetrieveGcpResourcesRegionStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveGcpResourcesRegionStep.class);
  private final CrlService crlService;
  private final GcpCloudContextService cloudContextService;
  private static final String REGIONS_PATH = "regions";

  public RetrieveGcpResourcesRegionStep(
      CrlService crlService, GcpCloudContextService cloudContextService) {
    this.crlService = crlService;
    this.cloudContextService = cloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(context.getWorkingMap(), CONTROLLED_RESOURCES_WITHOUT_REGION);
    List<ControlledResource> controlledResources =
        context.getWorkingMap().get(CONTROLLED_RESOURCES_WITHOUT_REGION, new TypeReference<>() {});
    Map<UUID, String> resourceIdToRegionMap = new HashMap<>();
    Map<UUID, String> resourceIdToWorkspaceIdMap = new HashMap<>();
    for (var resource : controlledResources) {
      WsmResourceType resourceType = resource.getResourceType();
      logger.info(
          "Getting cloud region for resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      switch (resourceType) {
        case CONTROLLED_GCP_GCS_BUCKET -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getGcsBucketRegion(resource.castByEnum(resourceType)));
        case CONTROLLED_GCP_BIG_QUERY_DATASET -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getBqDatasetRegion(resource.castByEnum(resourceType)));
        case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAiNotebookRegion(resource.castByEnum(resourceType)));
        default -> throw new UnsupportedOperationException(
            String.format(
                "resource of type %s is not a GCP resource or is a referenced resource",
                resourceType));
      }
    }
    context.getWorkingMap().put(CONTROLLED_RESOURCE_ID_TO_REGION_MAP, resourceIdToRegionMap);
    context
        .getWorkingMap()
        .put(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, resourceIdToWorkspaceIdMap);
    return StepResult.getStepResultSuccess();
  }

  private void populateMapsWithResourceIdKey(
      Map<UUID, String> resourceIdToRegionMap,
      Map<UUID, String> resourceIdToWorkspaceIdMap,
      ControlledResource resource,
      @Nullable String region) {
    if (region != null) {
      UUID resourceId = resource.getResourceId();
      resourceIdToRegionMap.put(resourceId, region);
      resourceIdToWorkspaceIdMap.put(resourceId, resource.getWorkspaceId().toString());
    }
  }

  @Nullable
  private String getBqDatasetRegion(ControlledBigQueryDatasetResource resource) {
    try {
      Dataset dataset =
          getBigQueryDataset(
              crlService.createWsmSaBigQueryCow(),
              resource.getProjectId(),
              resource.getDatasetName());
      return dataset.getLocation();
    } catch (IOException e) {
      logger.error(
          "Failed to get the cloud dataset instance for resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      return null;
    }
  }

  private String getGcsBucketRegion(ControlledGcsBucketResource resource) {
    String projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    StorageCow storageCow = crlService.createStorageCow(projectId);

    BucketCow existingBucketCow = storageCow.get(resource.getBucketName());
    return existingBucketCow.getBucketInfo().getLocation();
  }

  @Nullable
  private String getAiNotebookRegion(ControlledAiNotebookInstanceResource resource) {
    var projectId = cloudContextService.getRequiredGcpProject(resource.getWorkspaceId());
    String subnet;
    try {
      subnet =
          crlService
              .getAIPlatformNotebooksCow()
              .instances()
              .get(resource.toInstanceName(projectId))
              .execute()
              .getSubnet();
    } catch (IOException e) {
      logger.error(
          "Failed to get ai notebook cloud instance from resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      return null;
    }
    if (subnet == null) {
      logger.error("Ai notebook cloud instance has no subnet unexpectedly.");
      return null;
    }
    // Format of subnet is: projects/{project_id}/regions/{region}/subnetworks/{subnetwork_id}
    var paths = subnet.split("/");
    for (int i = 0; i < paths.length - 1; i++) {
      if (paths[i].equalsIgnoreCase(REGIONS_PATH)) {
        return paths[i + 1];
      }
    }
    logger.error("subnet {} has invalid format, failed to parse for region", subnet);
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // READ-ONLY step, do nothing here.
    return StepResult.getStepResultSuccess();
  }
}
