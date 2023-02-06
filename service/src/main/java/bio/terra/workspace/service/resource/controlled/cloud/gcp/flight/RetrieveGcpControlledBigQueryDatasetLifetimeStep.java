package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.crl.CrlService.getBigQueryDataset;
import static bio.terra.workspace.service.resource.model.WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
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

public class RetrieveGcpControlledBigQueryDatasetLifetimeStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveGcpResourcesRegionStep.class);
  private final CrlService crlService;

  public RetrieveGcpControlledBigQueryDatasetLifetimeStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getWorkingMap(), CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME);
    List<ControlledResource> controlledBigQueryDatasets =
        context
            .getWorkingMap()
            .get(CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME, new TypeReference<>() {});
    Map<UUID, Long> resourceIdToDefaultTableLifetimeMap = new HashMap<>();
    Map<UUID, Long> resourceIdToDefaultPartitionLifetimeMap = new HashMap<>();
    Map<UUID, String> resourceIdToWorkspaceIdMap = new HashMap<>();

    for (var resource : controlledBigQueryDatasets) {
      WsmResourceType resourceType = resource.getResourceType();
      logger.info(
          "Getting default table lifetime and partition life for resource (BigQuery dataset) {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      if (resourceType == CONTROLLED_GCP_BIG_QUERY_DATASET) {
        populateMapsWithResourceIdKey(
            resourceIdToDefaultTableLifetimeMap,
            resourceIdToDefaultPartitionLifetimeMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getBqDatasetDefaultTableLifetime(resource.castByEnum(resourceType)),
            getBqDatasetDefaultPartitionLifetime(resource.castByEnum(resourceType)));
      } else {
        throw new UnsupportedOperationException(
            String.format(
                "resource of type %s is not a controlled GCP BigQuery dataset", resourceType));
      }
    }
    context
        .getWorkingMap()
        .put(
            CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP,
            resourceIdToDefaultTableLifetimeMap);
    context
        .getWorkingMap()
        .put(
            CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP,
            resourceIdToDefaultPartitionLifetimeMap);
    context
        .getWorkingMap()
        .put(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, resourceIdToWorkspaceIdMap);
    return StepResult.getStepResultSuccess();
  }

  private void populateMapsWithResourceIdKey(
      Map<UUID, Long> resourceIdToDefaultTableLifetimeMap,
      Map<UUID, Long> resourceIdToDefaultPartitionLifetimeMap,
      Map<UUID, String> resourceIdToWorkspaceIdMap,
      ControlledResource resource,
      @Nullable Long defaultTableLifetime,
      @Nullable Long defaultPartitionLifetime) {
    if (defaultTableLifetime != null || defaultPartitionLifetime != null) {
      UUID resourceId = resource.getResourceId();
      resourceIdToDefaultTableLifetimeMap.put(resourceId, defaultTableLifetime);
      resourceIdToDefaultPartitionLifetimeMap.put(resourceId, defaultPartitionLifetime);
      resourceIdToWorkspaceIdMap.put(resourceId, resource.getWorkspaceId().toString());
    }
  }

  @Nullable
  private Long getBqDatasetDefaultTableLifetime(ControlledBigQueryDatasetResource resource) {
    try {
      Dataset dataset =
          getBigQueryDataset(
              crlService.createWsmSaBigQueryCow(),
              resource.getProjectId(),
              resource.getDatasetName());
      return dataset.getDefaultTableExpirationMs() / 1000;
    } catch (IOException e) {
      logger.error(
          "Failed to get the dataset default table lifetime for resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      return null;
    }
  }

  @Nullable
  private Long getBqDatasetDefaultPartitionLifetime(ControlledBigQueryDatasetResource resource) {
    try {
      Dataset dataset =
          getBigQueryDataset(
              crlService.createWsmSaBigQueryCow(),
              resource.getProjectId(),
              resource.getDatasetName());
      return dataset.getDefaultPartitionExpirationMs() / 1000;
    } catch (IOException e) {
      logger.error(
          "Failed to get the dataset default partition lifetime for resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      return null;
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // READ-ONLY step, do nothing here.
    return StepResult.getStepResultSuccess();
  }
}
