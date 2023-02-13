package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.crl.CrlService.getBigQueryDataset;
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

public class RetrieveControlledBigQueryDatasetLifetimeStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveControlledBigQueryDatasetLifetimeStep.class);
  private final CrlService crlService;

  public RetrieveControlledBigQueryDatasetLifetimeStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getWorkingMap(), CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME);
    List<ControlledBigQueryDatasetResource> controlledBigQueryDatasets =
        context
            .getWorkingMap()
            .get(CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME, new TypeReference<>() {});
    Map<UUID, String> resourceIdToDefaultTableLifetimeMap = new HashMap<>();
    Map<UUID, String> resourceIdToDefaultPartitionLifetimeMap = new HashMap<>();
    Map<UUID, String> resourceIdToWorkspaceIdMap = new HashMap<>();

    assert controlledBigQueryDatasets != null;
    for (var resource : controlledBigQueryDatasets) {
      logger.info(
          "Getting default table lifetime and partition life for resource (BigQuery dataset) {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      populateMapsWithResourceIdKey(
          resourceIdToDefaultTableLifetimeMap,
          resourceIdToDefaultPartitionLifetimeMap,
          resourceIdToWorkspaceIdMap,
          resource,
          getBqDatasetDefaultTableLifetime(resource),
          getBqDatasetDefaultPartitionLifetime(resource));
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
      Map<UUID, String> resourceIdToDefaultTableLifetimeMap,
      Map<UUID, String> resourceIdToDefaultPartitionLifetimeMap,
      Map<UUID, String> resourceIdToWorkspaceIdMap,
      ControlledResource resource,
      @Nullable Long defaultTableLifetime,
      @Nullable Long defaultPartitionLifetime) {
    UUID resourceId = resource.getResourceId();
    resourceIdToWorkspaceIdMap.put(resourceId, resource.getWorkspaceId().toString());
    if (defaultTableLifetime != null) {
      resourceIdToDefaultTableLifetimeMap.put(resourceId, defaultTableLifetime.toString());
    }
    if (defaultPartitionLifetime != null) {
      resourceIdToDefaultPartitionLifetimeMap.put(resourceId, defaultPartitionLifetime.toString());
    }
  }

  @Nullable
  private Long getBqDatasetDefaultTableLifetime(ControlledBigQueryDatasetResource resource) {
    Dataset dataset = getBqDataset(resource);
    if (dataset != null) {
      return dataset.getDefaultTableExpirationMs() / 1000;
    }
    return null;
  }

  @Nullable
  private Long getBqDatasetDefaultPartitionLifetime(ControlledBigQueryDatasetResource resource) {
    Dataset dataset = getBqDataset(resource);
    if (dataset != null) {
      return dataset.getDefaultPartitionExpirationMs() / 1000;
    }
    return null;
  }

  @Nullable
  private Dataset getBqDataset(ControlledBigQueryDatasetResource resource) {
    try {
      return getBigQueryDataset(
          crlService.createWsmSaBigQueryCow(), resource.getProjectId(), resource.getDatasetName());
    } catch (IOException e) {
      logger.error(
          "Failed to get dataset with resource ID {} in workspace {}: {}",
          resource.getResourceId(),
          resource.getWorkspaceId(),
          e.getMessage());
      return null;
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // READ-ONLY step, do nothing here.
    return StepResult.getStepResultSuccess();
  }
}
