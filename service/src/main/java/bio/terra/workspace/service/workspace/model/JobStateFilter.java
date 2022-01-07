package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiJobStateFilter;

public enum JobStateFilter {
  ALL(ApiJobStateFilter.ALL),
  ACTIVE(ApiJobStateFilter.ACTIVE),
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
