package bio.terra.workspace.common.exception;

import bio.terra.stairway.StepStatus;
import com.azure.core.management.exception.ManagementException;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

/**
 * Parses the AzureManagementException code for the reason of the failure.
 *
 * <p>Azure error codes can be found here:
 * https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
 * and https://docs.microsoft.com/en-us/rest/api/storageservices/blob-service-error-codes
 */
public class AzureManagementExceptionUtils {

  public static final String RESOURCE_NOT_FOUND = "ResourceNotFound";
  public static final String CONTAINER_NOT_FOUND = "ContainerNotFound";
  public static final String OPERATION_NOT_ALLOWED = "OperationNotAllowed";
  public static final String CONFLICT = "Conflict";
  public static final String SUBNETS_NOT_IN_SAME_VNET = "SubnetsNotInSameVnet";
  public static final String NIC_RESERVED_FOR_ANOTHER_VM = "NicReservedForAnotherVm";
  public static final String VM_EXTENSION_PROVISIONING_ERROR = "VMExtensionProvisioningError";

  /** Returns true iff the exception's code matches the supplied value. */
  public static boolean isExceptionCode(ManagementException ex, String exceptionCode) {
    return StringUtils.equals(ex.getValue().getCode(), exceptionCode);
  }

  public static Optional<HttpStatus> getHttpStatus(ManagementException me) {
    try {
      return Optional.of(HttpStatus.valueOf(me.getResponse().getStatusCode()));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  public static StepStatus maybeRetryStatus(ManagementException me) {
    return getHttpStatus(me)
        .filter(HttpStatus::is4xxClientError)
        .map(sc -> StepStatus.STEP_RESULT_FAILURE_FATAL)
        .orElse(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
