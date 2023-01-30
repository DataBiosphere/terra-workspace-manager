package bio.terra.workspace.service.resource.controlled.flight.update;

import static bio.terra.workspace.common.utils.FlightUtils.setResponse;
import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
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
    FlightMap workingMap = context.getWorkingMap();
    validateRequiredEntries(
        workingMap,
        CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP,
        CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP,
        CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP);

    Map<UUID, Long> resourceIdToDefaultTableLifetimeMap =
        workingMap.get(
            CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_TABLE_LIFETIME_MAP,
            new TypeReference<>() {});
    Map<UUID, Long> resourceIdToDefaultPartitionLifetimeMap =
        workingMap.get(
            CONTROLLED_BIG_QUERY_DATASET_RESOURCE_ID_TO_PARTITION_LIFETIME_MAP,
            new TypeReference<>() {});

    Map<UUID, String> resourceIdsToWorkspaceIdMap =
        workingMap.get(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, new TypeReference<>() {});
    List<ControlledResource> updatedResources = new ArrayList<>();

    for (var id : resourceIdsToWorkspaceIdMap.keySet()) {
      boolean updated =
          resourceDao.updateBigQueryDatasetDefaultTableLifetime(
                  id, resourceIdToDefaultTableLifetimeMap.get(id))
              && resourceDao.updateBigQueryDatasetDefaultPartitionLifetime(
                  id, resourceIdToDefaultPartitionLifetimeMap.get(id));
      if (updated) {
        updatedResources.add(
            resourceDao
                .getResource(UUID.fromString(resourceIdsToWorkspaceIdMap.get(id)), id)
                .castToControlledResource());
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
    if (resourceIdsToWorkspaceIdMap == null) {
      return StepResult.getStepResultSuccess();
    }
    for (var resourceId : resourceIdsToWorkspaceIdMap.keySet()) {
      String previousAttributes =
          context
              .getWorkingMap()
              .get(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
      resourceDao.updateResource(
          UUID.fromString(resourceIdsToWorkspaceIdMap.get(resourceId)),
          resourceId,
          null,
          null,
          previousAttributes,
          null);
    }
    return StepResult.getStepResultSuccess();
  }
}
