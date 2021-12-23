package bio.terra.workspace.service.workspace;

import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightFilter;
import bio.terra.stairway.FlightFilterOp;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.EnumeratedJob;
import bio.terra.workspace.service.workspace.model.EnumeratedJobs;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Alpha1Service {
  private static final Logger logger = LoggerFactory.getLogger(Alpha1Service.class);

  private final FeatureConfiguration features;
  private final WorkspaceService workspaceService;
  private final StairwayComponent stairwayComponent;

  public Alpha1Service(
      FeatureConfiguration features,
      WorkspaceService workspaceService,
      StairwayComponent stairwayComponent) {
    this.features = features;
    this.workspaceService = workspaceService;
    this.stairwayComponent = stairwayComponent;
  }

  public EnumeratedJobs enumerateJobs(
      UUID workspaceId,
      AuthenticatedUserRequest userRequest,
      int limit,
      String pageToken,
      @Nullable WsmResourceType resourceType,
      @Nullable StewardshipType stewardshipType,
      @Nullable String resourceName) {
    features.alpha1EnabledCheck();

    // TODO: For now the page token is just the offset.
    // We count on the page token being validated by the controller
    int offset = Integer.parseInt(pageToken);

    // Readers can see the workspace jobs list
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    List<FlightState> flightStateList;
    try {
      FlightFilter filter = buildFlightFilter(workspaceId, pageToken, resourceType, stewardshipType, resourceName);
      flightStateList = stairwayComponent.get().getFlights(offset, limit, filter);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }

    List<EnumeratedJob> jobList = new ArrayList<>();
    for (FlightState state : flightStateList) {
      FlightMap inputMap = state.getInputParameters();
      OperationType operationType =
          (inputMap.containsKey(ResourceKeys.OPERATION_TYPE)) ?
              inputMap.get(ResourceKeys.OPERATION_TYPE, OperationType.class) :
              OperationType.UNKNOWN;

      WsmResource wsmResource =
          (inputMap.containsKey(ResourceKeys.RESOURCE)) ?
              inputMap.get(ResourceKeys.RESOURCE, new TypeReference<>() {}) : null;

      String jobDescription =
          (inputMap.containsKey(JobMapKeys.DESCRIPTION.getKeyName())) ?
              inputMap.get(JobMapKeys.DESCRIPTION.getKeyName(), String.class) : StringUtils.EMPTY;

      EnumeratedJob enumeratedJob = new EnumeratedJob()
          .flightState(state)
          .jobDescription(jobDescription)
          .operationType(operationType)
          .resource(wsmResource);
      jobList.add(enumeratedJob);
    }

    return new EnumeratedJobs()
        .pageToken(String.format("%d", offset + limit))
        .totalResults(-1)
        .results(jobList);

  }

  private FlightFilter buildFlightFilter(
      UUID workspaceId,
      String pageToken, // TODO: when this is implemented it will use filtering
      @Nullable WsmResourceType resourceType,
      @Nullable StewardshipType stewardshipType,
      @Nullable String resourceName) {

    FlightFilter filter = new FlightFilter();
    // Always filter by workspace
    filter.addFilterInputParameter(
        WorkspaceFlightMapKeys.WORKSPACE_ID, FlightFilterOp.EQUAL, workspaceId.toString());
    // Add optional filters
    Optional.ofNullable(resourceType)
        .map(
            t ->
                filter.addFilterInputParameter(
                    ResourceKeys.RESOURCE_TYPE, FlightFilterOp.EQUAL, t));
    Optional.ofNullable(stewardshipType)
        .map(
            t ->
                filter.addFilterInputParameter(
                    ResourceKeys.STEWARDSHIP_TYPE, FlightFilterOp.EQUAL, t));
    Optional.ofNullable(resourceName)
        .map(
            t ->
                filter.addFilterInputParameter(
                    ResourceKeys.RESOURCE_NAME, FlightFilterOp.EQUAL, t));

    return filter;
  }
}
