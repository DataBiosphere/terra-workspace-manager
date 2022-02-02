package bio.terra.workspace.service.workspace;

import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightEnumeration;
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
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmCloudResourceType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.EnumeratedJob;
import bio.terra.workspace.service.workspace.model.EnumeratedJobs;
import bio.terra.workspace.service.workspace.model.JobStateFilter;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
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

  /**
   * List Stairway flights related to a workspace. These inputs are translated into inputs to
   * Stairway's getFlights calls. The resulting flights are translated into enumerated jobs. The
   * jobs are ordered by submit time.
   *
   * @param workspaceId workspace we are listing in
   * @param userRequest authenticated user
   * @param limit max number of jobs to return
   * @param pageToken optional starting place in the result set; start at beginning if missing
   * @param cloudResourceType optional filter by cloud resource type
   * @param stewardshipType optional filter by stewardship type
   * @param resourceName optional filter by resource name
   * @param jobStateFilter optional filter by job state
   * @return POJO containing the results
   */
  public EnumeratedJobs enumerateJobs(
      UUID workspaceId,
      AuthenticatedUserRequest userRequest,
      int limit,
      @Nullable String pageToken,
      @Nullable WsmCloudResourceType cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      @Nullable String resourceName,
      @Nullable JobStateFilter jobStateFilter) {
    features.alpha1EnabledCheck();

    // Readers can see the workspace jobs list
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SamWorkspaceAction.READ);

    FlightEnumeration flightEnumeration;
    try {
      FlightFilter filter =
          buildFlightFilter(
              workspaceId, cloudResourceType, stewardshipType, resourceName, jobStateFilter);
      flightEnumeration = stairwayComponent.get().getFlights(pageToken, limit, filter);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }

    List<EnumeratedJob> jobList = new ArrayList<>();
    for (FlightState state : flightEnumeration.getFlightStateList()) {
      FlightMap inputMap = state.getInputParameters();
      OperationType operationType =
          (inputMap.containsKey(WorkspaceFlightMapKeys.OPERATION_TYPE))
              ? inputMap.get(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.class)
              : OperationType.UNKNOWN;

      WsmResource wsmResource =
          (inputMap.containsKey(ResourceKeys.RESOURCE))
              ? inputMap.get(ResourceKeys.RESOURCE, new TypeReference<>() {})
              : null;

      String jobDescription =
          (inputMap.containsKey(JobMapKeys.DESCRIPTION.getKeyName()))
              ? inputMap.get(JobMapKeys.DESCRIPTION.getKeyName(), String.class)
              : StringUtils.EMPTY;

      EnumeratedJob enumeratedJob =
          new EnumeratedJob()
              .flightState(state)
              .jobDescription(jobDescription)
              .operationType(operationType)
              .resource(wsmResource);
      jobList.add(enumeratedJob);
    }

    return new EnumeratedJobs()
        .pageToken(flightEnumeration.getNextPageToken())
        .totalResults(flightEnumeration.getTotalFlights())
        .results(jobList);
  }

  private FlightFilter buildFlightFilter(
      UUID workspaceId,
      @Nullable WsmCloudResourceType cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      @Nullable String resourceName,
      @Nullable JobStateFilter jobStateFilter) {

    FlightFilter filter = new FlightFilter();
    // Always filter by workspace
    filter.addFilterInputParameter(
        WorkspaceFlightMapKeys.WORKSPACE_ID, FlightFilterOp.EQUAL, workspaceId.toString());
    // Add optional filters
    Optional.ofNullable(cloudResourceType)
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
    Optional.ofNullable(jobStateFilter).map(t -> addStateFilter(filter, t));

    return filter;
  }

  private FlightFilter addStateFilter(FlightFilter filter, JobStateFilter jobStateFilter) {
    switch (jobStateFilter) {
      case ALL:
        break;

      case ACTIVE:
        filter.addFilterCompletedTime(FlightFilterOp.EQUAL, null);
        break;

      case COMPLETED:
        filter.addFilterCompletedTime(FlightFilterOp.GREATER_THAN, Instant.EPOCH);
        break;
    }
    return filter;
  }
}
