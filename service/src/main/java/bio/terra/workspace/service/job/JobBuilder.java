package bio.terra.workspace.service.job;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.stairway.MonitoringHook;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.MdcHook;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.InvalidJobParameterException;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class JobBuilder {
  private final JobService jobService;
  private final StairwayComponent stairwayComponent;
  private final MdcHook mdcHook;
  private final FlightMap jobParameterMap;
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

  public JobBuilder(JobService jobService, StairwayComponent stairwayComponent, MdcHook mdcHook) {
    this.jobService = jobService;
    this.stairwayComponent = stairwayComponent;
    this.mdcHook = mdcHook;
    this.jobParameterMap = new FlightMap();
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
   * @return jobID of submitted flight
   */
  public String submit() {
    populateInputParams();
    return jobService.submit(flightClass, jobParameterMap, jobId);
  }

  /**
   * Submit a job to stairway, wait until it's complete, and return the job result.
   *
   * @param resultClass Class of the job's result
   * @return Result of the finished job.
   */
  @WithSpan
  public <T> T submitAndWait(Class<T> resultClass) {
    populateInputParams();
    return jobService.submitAndWait(
        flightClass, jobParameterMap, resultClass, /* typeReference= */ null, jobId);
  }

  /**
   * Submit a job to stairway, wait until it's complete, and return the job result.
   *
   * @param typeReference Class of the job's result
   * @return Result of the finished job.
   */
  @WithSpan
  public <T> T submitAndWait(TypeReference<T> typeReference) {
    populateInputParams();
    return jobService.submitAndWait(
        flightClass, jobParameterMap, /* resultClass= */ null, typeReference, jobId);
  }

  /**
   * Submit a job to stairway, wait until it's complete, and return the job result.
   *
   * @return Result of the finished job.
   */
  @WithSpan
  public <T> T submitAndWait() {
    populateInputParams();
    return jobService.submitAndWait(
        flightClass, jobParameterMap, /* resultClass= */ null, /* typeReference= */ null, jobId);
  }

  // Check the inputs, supply defaults and finalize the input parameter map
  private void populateInputParams() {
    if (flightClass == null) {
      throw new MissingRequiredFieldException("Missing flight class: flightClass");
    }

    if (operationType == null || operationType == OperationType.UNKNOWN) {
      throw new MissingRequiredFieldException("Missing or unspecified operation type");
    }

    // Default to a generated job id
    if (jobId == null) {
      jobId = stairwayComponent.get().createFlightId();
    }

    // Always add the MDC logging and tracing span parameters for the mdc hook
    addParameter(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());
    addParameter(
        MonitoringHook.SUBMISSION_SPAN_CONTEXT_MAP_KEY,
        MonitoringHook.serializeCurrentTracingContext(jobService.getOpenTelemetry()));

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
