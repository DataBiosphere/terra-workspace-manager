package bio.terra.workspace.service.job;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.TracingHook;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.MdcHook;
import bio.terra.workspace.db.ActivityLogDao;
import bio.terra.workspace.db.model.ActivityLogChangedType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService.JobResultOrException;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.InvalidJobParameterException;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.util.concurrent.FutureCallback;
import io.opencensus.contrib.spring.aop.Traced;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class JobBuilder {
  private final JobService jobService;
  private final StairwayComponent stairwayComponent;
  private final MdcHook mdcHook;
  private final FlightMap jobParameterMap;
  private final ActivityLogDao activityLogDao;
  private Class<? extends Flight> flightClass;
  @Nullable private String jobId;
  @Nullable private String description;
  @Nullable private Object request;
  @Nullable private AuthenticatedUserRequest userRequest;
  // Well-known keys used for filtering workspace jobs
  // All applicable ones of these should be supplied on every flight
  private String workspaceId;
  @Nullable private WsmResource resource;
  @Nullable private WsmResourceType resourceType;
  @Nullable private String resourceName;
  @Nullable private StewardshipType stewardshipType;
  @Nullable private OperationType operationType;

  public JobBuilder(
      JobService jobService,
      StairwayComponent stairwayComponent,
      MdcHook mdcHook,
      ActivityLogDao activityLogDao) {
    this.jobService = jobService;
    this.stairwayComponent = stairwayComponent;
    this.mdcHook = mdcHook;
    this.jobParameterMap = new FlightMap();
    this.activityLogDao = activityLogDao;
  }

  public JobBuilder flightClass(Class<? extends Flight> flightClass) {
    this.flightClass = flightClass;
    return this;
  }

  public JobBuilder jobId(@Nullable String jobId) {
    // If clients provide a non-null job ID, it cannot be whitespace-only
    if (StringUtils.isWhitespace(jobId)) {
      throw new InvalidJobIdException("jobId cannot be whitespace-only.");
    }
    this.jobId = jobId;
    return this;
  }

  public JobBuilder description(@Nullable String description) {
    this.description = description;
    return this;
  }

  public JobBuilder request(@Nullable Object request) {
    this.request = request;
    return this;
  }

  public JobBuilder userRequest(@Nullable AuthenticatedUserRequest userRequest) {
    this.userRequest = userRequest;
    return this;
  }

  public JobBuilder workspaceId(@Nullable String workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public JobBuilder resource(@Nullable WsmResource resource) {
    this.resource = resource;
    return this;
  }

  public JobBuilder resourceType(@Nullable WsmResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public JobBuilder resourceName(@Nullable String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public JobBuilder stewardshipType(@Nullable StewardshipType stewardshipType) {
    this.stewardshipType = stewardshipType;
    return this;
  }

  public JobBuilder operationType(@Nullable OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public JobBuilder addParameter(String keyName, @Nullable Object val) {
    if (StringUtils.isBlank(keyName)) {
      throw new InvalidJobParameterException("Parameter name cannot be null or blanks.");
    }
    // note that this call overwrites a parameter if it already exists
    jobParameterMap.put(keyName, val);
    return this;
  }

  /**
   * Submit a job to stairway and return the jobID immediately.
   *
   * @param changeType Specify the type of {@link ActivityLogChangedType} to be logged.
   * @param resultClass type for the response of the flight. When null, nothing is returned from the
   *     flight.
   * @return jobID of submitted flight
   */
  public <T> String submit(
      @Nullable ActivityLogChangedType changeType, @Nullable Class<T> resultClass) {
    populateInputParams();
    if (changeType == null) {
      return jobService.submit(flightClass, jobParameterMap, jobId);
    }
    return jobService.submitWithCallback(
        flightClass,
        resultClass,
        jobParameterMap,
        jobId,
        new FutureCallback<>() {
          @Override
          public void onSuccess(JobResultOrException<T> result) {
            if (changeType.equals(ActivityLogChangedType.DELETE)) {
              activityLogDao.setChangedDate(workspaceId, changeType);
            } else {
              if (result.getException() == null) {
                activityLogDao.setChangedDate(workspaceId, changeType);
              }
            }
          }

          @Override
          public void onFailure(Throwable t) {}
        });
  }

  /**
   * Submit a job to stairway, wait until it's complete, and return the job result.
   *
   * @param resultClass Class of the job's result
   * @param doAccessCheck whether to check user request's access to the job.
   * @param activityLogChangedType type of change activity to be logged when the job finishes. When
   *     null, nothing is logged.
   * @return Result of the finished job.
   */
  @Traced
  public <T> T submitAndWait(
      Class<T> resultClass,
      boolean doAccessCheck,
      @Nullable ActivityLogChangedType activityLogChangedType) {
    populateInputParams();
    try {
      T result =
          jobService.submitAndWait(flightClass, jobParameterMap, resultClass, jobId, doAccessCheck);
      activityLogDao.setChangedDate(workspaceId, activityLogChangedType);
      return result;
    } catch (Exception e) {
      if (activityLogChangedType != null
          && activityLogChangedType.equals(ActivityLogChangedType.DELETE)) {
        // DELETE job cannot be undone. so even when there is failure, we need to set a changed date
        // because the deletion still happened.
        activityLogDao.setChangedDate(workspaceId, activityLogChangedType);
      }
      throw e;
    }
  }

  /**
   * Submit the job and wait for it to finish.
   *
   * @param resultClass type of response to the flight
   * @param changedType type of change to be logged. When null, nothing is logged.
   * @return return the outcome of the flight.
   */
  @Traced
  public <T> T submitAndWait(Class<T> resultClass, @Nullable ActivityLogChangedType changedType) {
    return submitAndWait(resultClass, true, changedType);
  }

  // Check the inputs, supply defaults and finalize the input parameter map
  private void populateInputParams() {
    if (flightClass == null) {
      throw new MissingRequiredFieldException("Missing flight class: flightClass");
    }

    if (workspaceId == null) {
      throw new MissingRequiredFieldException("Missing workspace ID");
    }

    // Default to a generated job id
    if (jobId == null) {
      jobId = stairwayComponent.get().createFlightId();
    }

    // Always add the MDC logging and tracing span parameters for the mdc hook
    addParameter(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());
    addParameter(
        TracingHook.SUBMISSION_SPAN_CONTEXT_MAP_KEY, TracingHook.serializeCurrentTracingContext());

    // Convert any other members that were set into parameters. However, if they were
    // explicitly added with addParameter during construction, we do not overwrite them.
    if (shouldInsert(JobMapKeys.DESCRIPTION, description)) {
      addParameter(JobMapKeys.DESCRIPTION.getKeyName(), description);
    }
    if (shouldInsert(JobMapKeys.REQUEST, request)) {
      addParameter(JobMapKeys.REQUEST.getKeyName(), request);
    }
    if (shouldInsert(JobMapKeys.AUTH_USER_INFO, userRequest)) {
      addParameter(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
      addParameter(JobMapKeys.SUBJECT_ID.getKeyName(), userRequest.getSubjectId());
    }
    if (shouldInsert(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)) {
      addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    }
    if (shouldInsert(ResourceKeys.RESOURCE, resource)) {
      addParameter(ResourceKeys.RESOURCE, resource);
    }
    if (shouldInsert(ResourceKeys.RESOURCE_TYPE, resourceType)) {
      addParameter(ResourceKeys.RESOURCE_TYPE, resourceType);
    }
    if (shouldInsert(ResourceKeys.RESOURCE_NAME, resourceName)) {
      addParameter(ResourceKeys.RESOURCE_NAME, resourceName);
    }
    if (shouldInsert(ResourceKeys.STEWARDSHIP_TYPE, stewardshipType)) {
      addParameter(ResourceKeys.STEWARDSHIP_TYPE, stewardshipType);
    }
    if (shouldInsert(WorkspaceFlightMapKeys.OPERATION_TYPE, operationType)) {
      addParameter(WorkspaceFlightMapKeys.OPERATION_TYPE, operationType);
    }
  }

  private boolean shouldInsert(String mapKey, @Nullable Object value) {
    return (value != null && !jobParameterMap.containsKey(mapKey));
  }

  private boolean shouldInsert(JobMapKeys mapKey, @Nullable Object value) {
    return (value != null && !jobParameterMap.containsKey(mapKey.getKeyName()));
  }
}
