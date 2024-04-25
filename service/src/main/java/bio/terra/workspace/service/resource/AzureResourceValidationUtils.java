package bio.terra.workspace.service.resource;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.generated.model.ApiAzureVmImage;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** A collection of static validation functions specific to Azure */
@Component
public class AzureResourceValidationUtils {
  private static final Logger logger = LoggerFactory.getLogger(AzureResourceValidationUtils.class);

  private static final Pattern AZURE_RELAY_NAMESPACE_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]{0,78}[a-zA-Z0-9]$");

  /**
   * See
   * https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules
   * for azure resource rules
   */
  public static void validateAzureNamespace(String name) {
    if (!AZURE_RELAY_NAMESPACE_PATTERN.matcher(name).matches()
        || name.length() > 50
        || name.length() < 6) {
      logger.warn("Invalid Azure Namespace {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure Namespace specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static void validateAzureNetworkName(String name) {
    Pattern pattern = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-_.]{2,62}[a-zA-Z0-9_]$");

    if (!pattern.matcher(name).matches()) {
      logger.warn("Invalid Azure network name {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure network name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static void validateAzureCidrBlock(String range) {
    Pattern pattern =
        Pattern.compile(
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])(\\/(\\d|[1-2]\\d|3[0-2]))$");

    if (!pattern.matcher(range).matches()) {
      logger.warn("Invalid CIDR block {}", range);
      throw new InvalidReferenceException(
          "Invalid Azure CIDR block specified. See documentation for full specification https://stackoverflow.com/questions/18608165/cidr-notation-and-ip-range-validator-pattern/18611259.");
    }
  }

  // Azure Storage

  /**
   * Azure Storage Account name validation valid. An storage account name must be between 3-24
   * characters in length and may contain numbers and lowercase letters only.
   */
  private static final Pattern AZURE_STORAGE_ACCOUNT_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9]{3,24}$");

  /**
   * Azure Storage Container name validation. A storage container name must be between 3-63
   * characters in length and may contain numbers, lowercase letters, and dash (-) characters only.
   * It must start and end with a letter or number. Matching this pattern is necessary but not
   * sufficient for a valid container name (in particular, the dash character must be immediately
   * preceded and followed by a letter or number; consecutive dashes are not permitted).
   */
  private static final Pattern AZURE_STORAGE_CONTAINER_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-a-z0-9]{1,61}[a-z0-9]$");

  public static void validateAzureStorageAccountName(String storageAccountName) {
    if (!AZURE_STORAGE_ACCOUNT_NAME_VALIDATION_PATTERN.matcher(storageAccountName).matches()) {
      logger.warn("Invalid Storage Account name: {}", storageAccountName);
      throw new InvalidReferenceException(
          "Invalid Azure Storage Account name. The name must be 3 to 24 alphanumeric lower case characters.");
    }
  }

  public static void validateAzureStorageContainerName(String storageContainerName) {
    if (!AZURE_STORAGE_CONTAINER_NAME_VALIDATION_PATTERN.matcher(storageContainerName).matches()
        || storageContainerName.contains("--")) {
      logger.warn("Invalid Storage Container name: {}", storageContainerName);
      throw new InvalidReferenceException(
          "Invalid Azure Storage Container name. The name must be 3 to 63 alphanumeric lower case characters "
              + "or dashes, must start and end with a letter or number, and cannot contain consecutive dashes.");
    }
  }

  // Azure Disk

  public static void validateAzureDiskName(String name) {
    Pattern pattern = Pattern.compile("^[a-zA-Z0-9-_]{0,80}$");

    if (!pattern.matcher(name).matches()) {
      logger.warn("Invalid Disk name {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure Disk name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  // Azure Batch pool

  /** Batch Pool id must be 1-64 characters, using letters, numbers, dashes, and underscores */
  private static final Pattern AZURE_BATCH_POOL_ID_VALIDATION_PATTERN =
      Pattern.compile("^[-_a-zA-Z0-9]{1,64}$");

  private static final int AZURE_MAX_BATCH_POOL_DISPLAY_NAME = 1024;

  public static void validateAzureBatchPoolId(String id) {
    if (!AZURE_BATCH_POOL_ID_VALIDATION_PATTERN.matcher(id).matches()) {
      logger.warn("Invalid Azure Batch Pool id {}", id);
      throw new InvalidReferenceException(
          "Invalid Azure Batch Pool id specified. Name must be 1 to 64 alphanumeric characters or underscores or dashes.");
    }
  }

  public static void validateAzureBatchPoolDisplayName(@Nullable String displayName) {
    if (displayName != null && displayName.length() > AZURE_MAX_BATCH_POOL_DISPLAY_NAME) {
      throw new InvalidNameException(
          "Invalid display name specified. Display name must be under 1024 characters.");
    }
  }

  // Azure VM

  /**
   * See
   * https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules
   * for azure resource rules
   */
  public static void validateAzureIPorSubnetName(String name) {
    Pattern pattern = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-_.]{0,78}[a-zA-Z0-9_]$");

    if (!pattern.matcher(name).matches()) {
      logger.warn("Invalid Azure IP or Subnet name {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure IP or Subnet name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static final Pattern AZURE_MANAGED_IDENTITY_NAME_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][-a-zA-Z0-9_]{2,127}$");

  public static void validateAzureManagedIdentityName(String name) {
    if (name.toLowerCase().startsWith("pet-")) {
      // pet- is reserved for pet identities created by sam
      logger.warn("Invalid Managed Identity name {}", name);
      throw new InvalidReferenceException("Managed Identity name cannot start with 'pet-'.");
    }
    if (!AZURE_MANAGED_IDENTITY_NAME_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid Managed Identity name {}", name);
      throw new InvalidReferenceException(
          "Invalid Managed Identity name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static final Pattern AZURE_KUBERNETS_NAMESPACE_PATTERN =
      Pattern.compile("^[a-z][a-z0-9-]{0,62}$");

  public static void validateAzureKubernetesNamespace(String name) {
    // the namespace is a combination of the name of the resource and the workspace id
    // so the text of the error does not match the actual regex, it only talks about what the user
    // can control
    if (!AZURE_KUBERNETS_NAMESPACE_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid Kubernetes namespace {}", name);
      throw new InvalidReferenceException(
          "Invalid Kubernetes namespace resource name. Must be 1 to 26 characters long and can contain only lowercase alphanumeric characters or dashes. The name must start with a letter.");
    }
  }

  public static final Pattern AZURE_DATABASE_NAME_PATTERN =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,62}$");

  public static void validateAzureDatabaseName(String name) {
    if (!AZURE_DATABASE_NAME_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid Database name {}", name);
      throw new InvalidReferenceException(
          "Invalid Database name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static void validateAzureVmImage(ApiAzureVmImage vmImage) {
    if (StringUtils.isEmpty(vmImage.getUri())
        && StringUtils.isEmpty(vmImage.getPublisher())
        && StringUtils.isEmpty(vmImage.getOffer())
        && StringUtils.isEmpty(vmImage.getSku())
        && StringUtils.isEmpty(vmImage.getVersion())) {
      throw new MissingRequiredFieldException(
          "Missing required fields for vmImage. Either uri or publisher, offer, sku, version should be defined.");
    }
    if (StringUtils.isEmpty(vmImage.getUri())
        && (StringUtils.isEmpty(vmImage.getPublisher())
            || StringUtils.isEmpty(vmImage.getOffer())
            || StringUtils.isEmpty(vmImage.getSku())
            || StringUtils.isEmpty(vmImage.getVersion()))) {
      throw new MissingRequiredFieldException(
          "Missing required fields for vmImage. Publisher, offer, sku, version should be defined.");
    }
  }
}
