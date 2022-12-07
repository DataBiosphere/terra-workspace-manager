package bio.terra.workspace.common.utils;

import com.azure.core.management.exception.ManagementException;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses the Azure ManagementException code for the reason of the failure.
 *
 * <p>Azure error codes can be found here:
 * https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
 * and https://docs.microsoft.com/en-us/rest/api/storageservices/blob-service-error-codes
 */
public class ManagementExceptionUtils {

  public static final String RESOURCE_NOT_FOUND = "ResourceNotFound";
  public static final String CONTAINER_NOT_FOUND = "ContainerNotFound";
  public static final String CONFLICT = "Conflict";
  public static final String SUBNETS_NOT_IN_SAME_VNET = "SubnetsNotInSameVnet";
  public static final String NIC_RESERVED_FOR_ANOTHER_VM = "NicReservedForAnotherVm";
  public static final String SUBNET_IS_FULL = "SubnetIsFull";
  public static final String IMAGE_NOT_FOUND = "ImageNotFound";
  public static final String INVALID_PARAMETER = "InvalidParameter";

  /** Returns true iff the exception's code matches the supplied value. */
  public static boolean isExceptionCode(ManagementException ex, String exceptionCode) {
    return StringUtils.equals(ex.getValue().getCode(), exceptionCode);
  }
}
