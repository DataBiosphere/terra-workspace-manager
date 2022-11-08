package bio.terra.workspace.service.resource.controlled.cloud.azure;

import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.storage.blob.models.BlobCopyInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BlobCopierResult(
    Map<LongRunningOperationStatus, List<PollResponse<BlobCopyInfo>>> pollingResults) {

  private static final Set<LongRunningOperationStatus> ERROR_POLL_STATES =
      Set.of(
          LongRunningOperationStatus.FAILED,
          LongRunningOperationStatus.IN_PROGRESS,
          LongRunningOperationStatus.USER_CANCELLED,
          LongRunningOperationStatus.NOT_STARTED);

    /**
     * Returns true if any blob copy operation is not successful.
     *
     * Note that we consider LongRunningOperationStatus.IN_PROGRESS as not successful, since the operation is still
     * in progress and may eventually fail out.
     */
  public boolean anyFailures() {
    return pollingResults.keySet().stream().anyMatch(ERROR_POLL_STATES::contains);
  }
}
