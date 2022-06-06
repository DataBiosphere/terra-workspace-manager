package bio.terra.workspace.common.utils;

import com.azure.core.management.exception.ManagementException;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses the Azure ManagementException code for the reason of the failure.
 *
 * Azure error codes can be found here:
 *   https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
 */
public class ManagementExceptionUtils {

    /**
     * Was the cause of the Azure ManagementException `ResourceNotFound`?
     */
    public static boolean isResourceNotFound(ManagementException ex) {
        return StringUtils.equals(ex.getValue().getCode(), "ResourceNotFound");
    }

    /**
     * Was the cause of the Azure ManagementException `ContainerNotFound`?
     *
     * See https://docs.microsoft.com/en-us/dotnet/api/microsoft.azure.cosmos.table.storageerrorcodestrings
     */
    public static boolean isContainerNotFound(ManagementException ex) {
        return StringUtils.equals(ex.getValue().getCode(), "ContainerNotFound");
    }

    /**
     * Was the cause of the Azure ManagementException `Conflict`?
     */
    public static boolean isConflict(ManagementException ex) {
        return StringUtils.equals(ex.getValue().getCode(), "Conflict");
    }

    /**
     * Was the cause of the Azure ManagementException `SubnetsNotInSameVnet`?
     */
    public static boolean isSubnetsNotInSameVnet(ManagementException ex) {
        return StringUtils.equals(ex.getValue().getCode(), "SubnetsNotInSameVnet");
    }
}
