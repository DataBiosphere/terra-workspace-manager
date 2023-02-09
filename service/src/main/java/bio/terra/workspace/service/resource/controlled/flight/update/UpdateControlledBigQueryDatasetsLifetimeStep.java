package bio.terra.workspace.service.resource.controlled.flight.update;

import static bio.terra.workspace.common.utils.FlightUtils.setResponse;
import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.resource.model.WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UpdateControlledBigQueryDatasetsLifetimeStep implements Step {
  private final ResourceDao resourceDao;

  public UpdateControlledBigQueryDatasetsLifetimeStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var workingMap = context.getWorkingMap();
    validateRequiredEntries(
        workingMap,
        CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP,
        CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP,
        CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP);

    Map<UUID, String> resourceIdToDefaultTableLifetimeMap =
        workingMap.get(
            CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP,
            new TypeReference<HashMap<UUID, String>>() {});
    Map<UUID, String> resourceIdToDefaultPartitionLifetimeMap =
        workingMap.get(
            CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP,
            new TypeReference<HashMap<UUID, String>>() {});

    Map<UUID, String> resourceIdsToWorkspaceIdMap =
        workingMap.get(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, new TypeReference<>() {});
    List<ControlledBigQueryDatasetResource> updatedResources = new ArrayList<>();

    List<ControlledResource> controlledBigQueryDatasets =
        context
            .getWorkingMap()
            .get(CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME, new TypeReference<>() {});

    for (var resource : controlledBigQueryDatasets) {
      var id = resource.getResourceId();
      boolean updated =
          resourceDao.updateBigQueryDatasetDefaultTableAndPartitionLifetime(
              resource.castByEnum(CONTROLLED_GCP_BIG_QUERY_DATASET),
              Long.valueOf(resourceIdToDefaultTableLifetimeMap.get(id)),
              Long.valueOf(resourceIdToDefaultPartitionLifetimeMap.get(id)));

      if (updated) {
        updatedResources.add(
            resourceDao
                .getResource(UUID.fromString(resourceIdsToWorkspaceIdMap.get(id)), id)
                .castToControlledResource()
                .castByEnum(CONTROLLED_GCP_BIG_QUERY_DATASET));
      }
    }

    setResponse(context, updatedResources, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    Map<UUID, String> resourceIdsToWorkspaceIdMap =
        context
            .getWorkingMap()
            .get(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, new TypeReference<>() {});

    List<ControlledResource> controlledBigQueryDatasets =
        context
            .getWorkingMap()
            .get(CONTROLLED_BIG_QUERY_DATASETS_WITHOUT_LIFETIME, new TypeReference<>() {});

    if (controlledBigQueryDatasets == null || controlledBigQueryDatasets.isEmpty()) {
      return StepResult.getStepResultSuccess();
    }
    for (var resource : controlledBigQueryDatasets) {
      resourceDao.updateBigQueryDatasetDefaultTableAndPartitionLifetime(
          resource.castByEnum(CONTROLLED_GCP_BIG_QUERY_DATASET), null, null);
    }
    return StepResult.getStepResultSuccess();
  }
}
