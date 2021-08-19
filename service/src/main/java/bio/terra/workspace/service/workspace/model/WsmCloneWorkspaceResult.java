package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;

/** Internal wrapper type for {@link ApiCloneWorkspaceResult} */
public class WsmCloneWorkspaceResult {
  private ApiJobReport jobReport;
  private ApiErrorReport errorReport;
  private WsmClonedWorkspace workspace;

  public WsmCloneWorkspaceResult() {}

  public ApiJobReport getJobReport() {
    return jobReport;
  }

  public void setJobReport(ApiJobReport jobReport) {
    this.jobReport = jobReport;
  }

  public ApiErrorReport getErrorReport() {
    return errorReport;
  }

  public void setErrorReport(ApiErrorReport errorReport) {
    this.errorReport = errorReport;
  }

  public WsmClonedWorkspace getWorkspace() {
    return workspace;
  }

  public void setWorkspace(WsmClonedWorkspace workspace) {
    this.workspace = workspace;
  }

  public ApiCloneWorkspaceResult toApiModel() {
    return new ApiCloneWorkspaceResult()
        .jobReport(jobReport)
        .errorReport(errorReport)
        .workspace(workspace.toApiModel());
  }
}
