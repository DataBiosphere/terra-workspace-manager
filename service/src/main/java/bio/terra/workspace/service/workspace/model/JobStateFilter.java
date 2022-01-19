package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiJobStateFilter;

/** Simple enumeration for the job state filter used in enumerateJobs */
public enum JobStateFilter {
  /** include jobs in all states in the result */
  ALL(ApiJobStateFilter.ALL),
  /** include only active jobs (running, queued, waiting, etc) in the result */
  ACTIVE(ApiJobStateFilter.ACTIVE),
  /** include only completed jobs (success, error) in the result */
  COMPLETED(ApiJobStateFilter.COMPLETED);

  private final ApiJobStateFilter jobStateFilter;

  JobStateFilter(ApiJobStateFilter jobStateFilter) {
    this.jobStateFilter = jobStateFilter;
  }

  public ApiJobStateFilter toApi() {
    return jobStateFilter;
  }

  public static JobStateFilter fromApi(ApiJobStateFilter jobStateType) {
    if (jobStateType == null) {
      return null;
    }
    for (JobStateFilter state : JobStateFilter.values()) {
      if (state.jobStateFilter == jobStateType) {
        return state;
      }
    }
    throw new IllegalArgumentException("Invalid ApiJobStateFilter");
  }
}
