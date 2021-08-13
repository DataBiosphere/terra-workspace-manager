package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Internal wrapper type for {@link ApiCloneWorkspaceResult} */
public class WsmCloneWorkspaceResult {
  private ApiJobReport jobReport;
  private ApiErrorReport errorReport;
  private UUID sourceWorkspaceId;
  private UUID destinationWorkspaceId;
  private List<WsmResourceCloneDetails> resources;

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

  public UUID getSourceWorkspaceId() {
    return sourceWorkspaceId;
  }

  public void setSourceWorkspaceId(UUID sourceWorkspaceId) {
    this.sourceWorkspaceId = sourceWorkspaceId;
  }

  public UUID getDestinationWorkspaceId() {
    return destinationWorkspaceId;
  }

  public void setDestinationWorkspaceId(UUID destinationWorkspaceId) {
    this.destinationWorkspaceId = destinationWorkspaceId;
  }

  public List<WsmResourceCloneDetails> getResources() {
    return resources;
  }

  public void setResources(List<WsmResourceCloneDetails> resources) {
    this.resources = resources;
  }

  public ApiCloneWorkspaceResult toApiModel() {
    return new ApiCloneWorkspaceResult()
        .jobReport(jobReport)
        .errorReport(errorReport)
        .sourceWorkspaceId(sourceWorkspaceId)
        .destinationWorkspaceId(destinationWorkspaceId)
        .resources(
            resources.stream()
                .map(WsmResourceCloneDetails::toApiModel)
                .collect(Collectors.toList()));
  }
}
