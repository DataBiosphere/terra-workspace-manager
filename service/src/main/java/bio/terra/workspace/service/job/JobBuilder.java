package bio.terra.workspace.service.job;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.InvalidJobParameterException;
import io.opencensus.contrib.spring.aop.Traced;

public class JobBuilder {

  private final JobService jobServiceRef;
  private final Class<? extends Flight> flightClass;
  private final FlightMap jobParameterMap;
  private final String jobId;

  // constructor only takes required parameters
  public JobBuilder(
      String description,
      String jobId,
      Class<? extends Flight> flightClass,
      Object request,
      AuthenticatedUserRequest userRequest,
      JobService jobServiceRef) {
    this.jobServiceRef = jobServiceRef;
    this.flightClass = flightClass;
    this.jobId = jobId;

    // initialize with required parameters
    this.jobParameterMap = new FlightMap();
    jobParameterMap.put(JobMapKeys.DESCRIPTION.getKeyName(), description);
    jobParameterMap.put(JobMapKeys.REQUEST.getKeyName(), request);
    jobParameterMap.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    jobParameterMap.put(JobMapKeys.SUBJECT_ID.getKeyName(), userRequest.getSubjectId());
  }

  // use addParameter method for optional parameter
  // returns the JobBuilder object to allow method chaining
  public JobBuilder addParameter(String keyName, Object val) {
    if (keyName == null) {
      throw new InvalidJobParameterException("Parameter name cannot be null.");
    }

    // check that keyName doesn't match one of the required parameter names
    // i.e. disallow overwriting one of the required parameters
    if (JobMapKeys.isRequiredKey(keyName)) {
      throw new InvalidJobParameterException(
          "Required parameters can only be set by the constructor. (" + keyName + ")");
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
    return jobServiceRef.submit(flightClass, jobParameterMap, jobId);
  }

  /**
   * Submit a job to stairway, wait until it's complete, and return the job result.
   *
   * @param resultClass Class of the job's result
   * @return Result of the finished job.
   */
  @Traced
  public <T> T submitAndWait(Class<T> resultClass) {
    return jobServiceRef.submitAndWait(flightClass, jobParameterMap, resultClass, jobId);
  }
}
