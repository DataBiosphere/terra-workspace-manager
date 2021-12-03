package bio.terra.workspace.service.resource;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.azure.core.management.Region;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A collection of static validation functions */
public class ValidationUtils {
  private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);

  /**
   * GCS bucket name validation is somewhat complex due to rules about usage of "." and restricted
   * names like "goog", but as a baseline they must be 3-222 characters long using lowercase
   * letters, numbers, dashes, underscores, and dots, and must start and end with a letter or
   * number. Matching this pattern is necessary but not sufficient for a valid bucket name.
   */
  public static final Pattern BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-_.a-z0-9]{1,220}[a-z0-9]$");

  /**
   * Azure Storage Account name validation valid. An storage account name must be between 3-24
   * characters in length and may contain numbers and lowercase letters only.
   */
  public static final Pattern AZURE_STORAGE_ACCOUNT_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9]{3,24}$");

  /**
   * BigQuery datasets must be 1-1024 characters, using letters (upper or lowercase), numbers, and
   * underscores.
   */
  public static final Pattern BQ_DATASET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[_a-zA-Z0-9]{1,1024}$");

  /**
   * AI Notebook instances must be 1-63 characters, using lower case letters, numbers, and dashes.
   * The first character must be a lower case letter, and the last character must not be a dash.
   */
  public static final Pattern AI_NOTEBOOK_INSTANCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)");

  public static void validateBucketName(String name) {
    if (StringUtils.isEmpty(name) || !BUCKET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidReferenceException(
          "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, dashes, and underscores. See Google documentation for the full specification.");
    }
  }

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

  public static void validateAzureNetworkName(String name) {
    Pattern pattern = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-_.]{2,62}[a-zA-Z0-9_]$");

    if (!pattern.matcher(name).matches()) {
      logger.warn("Invalid Azure network name {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure network name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static void validateAzureDiskName(String name) {
    Pattern pattern = Pattern.compile("^[a-zA-Z0-9-_]{0,80}$");

    if (!pattern.matcher(name).matches()) {
      logger.warn("Invalid Disk name {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure Disk name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
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

  public static void validateBqDatasetName(String name) {
    if (StringUtils.isEmpty(name) || !BQ_DATASET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid BQ name {}", name);
      throw new InvalidReferenceException(
          "Invalid BQ dataset name specified. Name must be 1 to 1024 alphanumeric characters or underscores.");
    }
  }

  public static void validateAiNotebookInstanceId(String name) {
    if (!AI_NOTEBOOK_INSTANCE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid AI Notebook instance ID {}", name);
      throw new InvalidReferenceException(
          "Invalid AI Notebook instance ID specified. ID must be 1 to 63 alphanumeric lower case characters or dashes, where the first character is a lower case letter.");
    }
  }

  public static void validate(ApiGcpAiNotebookInstanceCreationParameters creationParameters) {
    validateAiNotebookInstanceId(creationParameters.getInstanceId());
    // OpenApi one-of fields aren't being generated correctly, so we do manual one-of fields.
    if ((creationParameters.getVmImage() == null)
        == (creationParameters.getContainerImage() == null)) {
      throw new InconsistentFieldsException(
          "Exactly one of vmImage or containerImage must be specified.");
    }
    validate(creationParameters.getVmImage());
  }

  private static void validate(ApiGcpAiNotebookInstanceVmImage vmImage) {
    if (vmImage == null) {
      return;
    }
    if ((vmImage.getImageName() == null) == (vmImage.getImageFamily() == null)) {
      throw new InconsistentFieldsException(
          "Exactly one of imageName or imageFamily must be specified for a valid vmImage.");
    }
  }

  public static void validateResourceName(String name) {
    // TODO: Decide what name validation we should do for resource names. My suggestion is to match
    // TDR
    //  with a 512 character name that cannot being with an underscore. That gives us room to
    // generate
    //  names based on the resource name. It also is roomy.
  }

  public static void validateStorageAccountName(String storageAccountName) {
    if (!AZURE_STORAGE_ACCOUNT_NAME_VALIDATION_PATTERN.matcher(storageAccountName).matches()) {
      logger.warn("Invalid Storage Account name: {}", storageAccountName);
      throw new InvalidReferenceException(
          "Invalid Azure Storage Account name. The name must be 3 to 24 alphanumeric lower case characters.");
    }
  }

  public static void validateAzureVmSize(String vmSize) {
    if (!VirtualMachineSizeTypes.values().stream()
        .map(x -> x.toString())
        .collect(Collectors.toList())
        .contains(vmSize)) {
      logger.warn("Invalid Azure vmSize {}", vmSize);
      throw new InvalidReferenceException(
          "Invalid Azure vm size specified. See the class `com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes`");
    }
  }

  public static void validateRegion(String region) {
    if (!Region.values().stream()
        .map(x -> x.toString())
        .collect(Collectors.toList())
        .contains(region)) {
      logger.warn("Invalid Azure region {}", region);
      throw new InvalidReferenceException(
          "Invalid Azure Region specified. See the class `com.azure.core.management.Region`");
    }
  }
}
