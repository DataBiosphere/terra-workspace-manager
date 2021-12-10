package bio.terra.workspace.service.job;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.TracingHook;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.MdcHook;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.InvalidJobParameterException;
import io.opencensus.contrib.spring.aop.Traced;
import org.apache.commons.lang3.StringUtils;

public class JobBuilder {
  private final JobService jobService;
  private final StairwayComponent stairwayComponent;
  private final MdcHook mdcHook;
  private final FlightMap jobParameterMap;
  private Class<? extends Flight> flightClass;
  private String jobId;
  private String description;
  private Object request;
  private AuthenticatedUserRequest userRequest;

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

  public JobBuilder jobId(String jobId) {
    if (jobId != null) {
      // If clients provide a non-null job ID, it cannot be whitespace-only
      if (StringUtils.isWhitespace(jobId)) {
        throw new InvalidJobIdException("jobId cannot be whitespace-only.");
      }
    }
    this.jobId = jobId;
    return this;
  }

  public JobBuilder description(String description) {
    this.description = description;
    return this;
  }

  public JobBuilder request(Object request) {
    this.request = request;
    return this;
  }

  public JobBuilder userRequest(AuthenticatedUserRequest userRequest) {
    this.userRequest = userRequest;
    return this;
  }

  public JobBuilder addParameter(String keyName, Object val) {
    if (keyName == null) {
      throw new InvalidJobParameterException("Parameter name cannot be null.");
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
    validateAndDefault();
    return jobService.submit(flightClass, jobParameterMap, jobId);
  }

  /**
   * Submit a job to stairway, wait until it's complete, and return the job result.
   *
   * @param resultClass Class of the job's result
   * @return Result of the finished job.
   */
  @Traced
  public <T> T submitAndWait(Class<T> resultClass) {
    validateAndDefault();
    return jobService.submitAndWait(flightClass, jobParameterMap, resultClass, jobId);
  }

  // Check the inputs, supply defaults and finalize the input parameter map
  private void validateAndDefault() {
    if (flightClass == null) {
      throw new MissingRequiredFieldException("Missing flight class: flightClass");
    }

    // Default to a generated job id
    if (jobId == null) {
      jobId = stairwayComponent.get().createFlightId();
    }

    // Always add the MDC logging and tracing span parameters for the mdc hook
    addParameter(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());
    addParameter(
        TracingHook.SUBMISSION_SPAN_CONTEXT_MAP_KEY, TracingHook.serializeCurrentTracingContext());

    // Convert the any other members that were set into parameters. However, if they wer
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
  }

  private boolean shouldInsert(JobMapKeys mapKey, Object value) {
    return (value != null && !jobParameterMap.containsKey(mapKey.getKeyName()));
  }
}
