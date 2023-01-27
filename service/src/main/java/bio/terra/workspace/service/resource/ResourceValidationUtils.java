package bio.terra.workspace.service.resource;

import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.app.configuration.external.GitRepoReferencedResourceConfiguration;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.exception.InvalidControlledResourceException;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.azure.core.management.Region;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A collection of static validation functions */
@Component
public class ResourceValidationUtils {

  private static final Logger logger = LoggerFactory.getLogger(ResourceValidationUtils.class);

  /**
   * GCS bucket name validation is somewhat complex due to rules about usage of "." and restricted
   * names like "goog", but as a baseline they must be 3-222 characters long using lowercase
   * letters, numbers, dashes, underscores, and dots, and must start and end with a letter or
   * number. Matching this pattern is necessary but not sufficient for a valid bucket name.
   */
  public static final Pattern REFERENCED_BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-_.a-z0-9]{1,220}[a-z0-9]$");

  /**
   * Similar to REFERENCED_BUCKET_NAME_VALIDATION_PATTERN, except prohibits underscores. GCP
   * recommends against underscores in bucket names because DNS hostnames can't have underscores. In
   * particular, Nextflow fails if bucket name has underscore (because bucket name isn't valid DNS
   * hostname.)
   */
  public static final Pattern CONTROLLED_BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-.a-z0-9]{1,220}[a-z0-9]$");

  private static final String REFERENCED_BUCKET_NAME_VALIDATION_FAILURE_ERROR =
      "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, dashes, and underscores. See Google documentation for the full specification.";

  private static final String CONTROLLED_BUCKET_NAME_VALIDATION_FAILURE_ERROR =
      "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, and dashes. See Google documentation for the full specification.";

  /**
   * Azure Storage Account name validation valid. An storage account name must be between 3-24
   * characters in length and may contain numbers and lowercase letters only.
   */
  public static final Pattern AZURE_STORAGE_ACCOUNT_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9]{3,24}$");

  /**
   * Azure Storage Container name validation. A storage container name must be between 3-63
   * characters in length and may contain numbers, lowercase letters, and dash (-) characters only.
   * It must start and end with a letter or number. Matching this pattern is necessary but not
   * sufficient for a valid container name (in particular, the dash character must be immediately
   * preceded and followed by a letter or number; consecutive dashes are not permitted).
   */
  public static final Pattern AZURE_STORAGE_CONTAINER_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-a-z0-9]{1,61}[a-z0-9]$");

  /**
   * BigQuery datasets must be 1-1024 characters, using letters (upper or lowercase), numbers, and
   * underscores.
   */
  public static final Pattern BQ_DATASET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[_a-zA-Z0-9]{1,1024}$");

  /**
   * BigQuery data table name must be 1-1024 characters, contains Unicode characters in category L
   * (letter), M (mark), N (number), Pc (connector, including underscore), Pd (dash), Zs (space).
   */
  public static final Pattern BQ_DATATABLE_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[\\p{L}\\p{N}\\p{Pc}\\p{Pd}\\p{Zs}\\p{M}]{1,1024}$");

  /**
   * AI Notebook instances must be 1-63 characters, using lower case letters, numbers, and dashes.
   * The first character must be a lower case letter, and the last character must not be a dash.
   */
  public static final Pattern AI_NOTEBOOK_INSTANCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)");

  /**
   * Resource names must be 1-1024 characters, using letters, numbers, dashes, and underscores and
   * must not start with a dash or underscore.
   */
  public static final Pattern RESOURCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][-_a-zA-Z0-9]{0,1023}$");

  public static final Pattern AZURE_RELAY_NAMESPACE_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]{0,78}[a-zA-Z0-9]$");

  /** Batch Pool id must be -64 characters, using letters, numbers, dashes, and underscores */
  public static final Pattern AZURE_BATCH_POOL_ID_VALIDATION_PATTERN =
      Pattern.compile("^[-_a-zA-Z0-9]{0,63}$");

  // An object named "." or ".." is nearly impossible for a user to delete.
  private static final ImmutableList<String> DISALLOWED_OBJECT_NAMES = ImmutableList.of(".", "..");

  // Pattern for Git SSH URL. It often but doesn't have to have the .git extension.
  private static final Pattern GIT_SSH_URI_PATTERN = Pattern.compile("git@(.*?)\\:(.*)$");
  /**
   * Magic prefix for ACME HTTP challenge.
   *
   * <p>See https://tools.ietf.org/html/draft-ietf-acme-acme-09#section-8.3
   */
  public static final String ACME_CHALLENGE_PREFIX = ".well-known/acme-challenge/";

  private static final String GOOG_PREFIX = "goog";
  private static final ImmutableList<String> GOOGLE_NAMES = ImmutableList.of("google", "g00gle");
  private static final int MAX_RESOURCE_DESCRIPTION_NAME = 2048;
  private static final int MAX_BATCH_POOL_DISPLAY_NAME = 1024;

  private final GitRepoReferencedResourceConfiguration gitRepoReferencedResourceConfiguration;

  @Autowired
  public ResourceValidationUtils(
      GitRepoReferencedResourceConfiguration gitRepoReferencedResourceConfiguration) {
    this.gitRepoReferencedResourceConfiguration = gitRepoReferencedResourceConfiguration;
  }

  public static void validateBucketNameDisallowUnderscore(String name) {
    validateBucketName(
        name,
        CONTROLLED_BUCKET_NAME_VALIDATION_PATTERN,
        CONTROLLED_BUCKET_NAME_VALIDATION_FAILURE_ERROR);
  }

  public static void validateBucketNameAllowsUnderscore(String name) {
    validateBucketName(
        name,
        REFERENCED_BUCKET_NAME_VALIDATION_PATTERN,
        REFERENCED_BUCKET_NAME_VALIDATION_FAILURE_ERROR);
  }

  /**
   * Validates gcs-bucket name following Google documentation
   * https://cloud.google.com/storage/docs/naming-buckets#requirements on a best-effort base.
   *
   * <p>This method DOES NOT guarantee that the bucket name is valid.
   *
   * @param name gcs-bucket name
   * @param validationFailureError
   * @throws InvalidNameException throws exception when the bucket name fails to conform to the
   *     Google naming convention for bucket name.
   */
  @VisibleForTesting
  public static void validateBucketName(
      String name, Pattern validationPattern, String validationFailureError) {
    if (StringUtils.isEmpty(name) || !validationPattern.matcher(name).matches()) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidNameException(validationFailureError);
    }
    for (String s : name.split("\\.")) {
      if (s.length() > 63) {
        logger.warn("Invalid bucket name {}", name);
        throw new InvalidNameException(
            "Invalid GCS bucket name specified. Names containing dots can contain up to 222 characters, but each dot-separated component can be no longer than 63 characters. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
      }
    }
    if (name.startsWith(GOOG_PREFIX)) {
      logger.warn("Invalid bucket name {}", name);
      throw new InvalidNameException(
          "Invalid GCS bucket name specified. Bucket names cannot have prefix goog. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
    }
    for (String google : GOOGLE_NAMES) {
      if (name.contains(google)) {
        logger.warn("Invalid bucket name {}", name);
        throw new InvalidNameException(
            "Invalid GCS bucket name specified. Bucket names cannot contains google or mis-spelled google. See Google documentation https://cloud.google.com/storage/docs/naming-buckets#requirements for the full specification.");
      }
    }
  }

  public static void validateControlledResourceRegionAgainstPolicy(
      TpsApiDispatch tpsApiDispatch, UUID workspaceUuid, String location, CloudPlatform platform) {
    switch (platform) {
      case AZURE -> validateAzureRegion(location);
      case GCP -> validateGcpRegion(tpsApiDispatch, workspaceUuid, location);
      default -> throw new InvalidControlledResourceException("Unrecognized platform");
    }
  }

  /** Validate whether the input URI is a valid GitHub Repo https uri. */
  public void validateGitRepoUri(String gitUri) {
    if (gitUri == null) {
      throw new InvalidReferenceException("Git repo uri is null but it is required.");
    }
    try {
      URI uri = new URI(gitUri);
      if (("https".equals(uri.getScheme()) || "ssh".equals(uri.getScheme()))
          && hasValidHostName(uri.getHost())) {
        return;
      }
    } catch (URISyntaxException e) {
      // The SSH url that does not have a scheme cannot be parsed into a URI object. So we use
      // regex to extract the host name and validate if it is a valid host name.
      if (validateSshUri(gitUri)) {
        return;
      }
      logger.warn("Git repo repo uri {} has syntax error", gitUri);
      throw new InvalidReferenceException("Invalid git repo uri", e);
    }
    throw new InvalidReferenceException("Invalid git repo uri");
  }

  private boolean validateSshUri(String gitUri) {
    Matcher matcher = GIT_SSH_URI_PATTERN.matcher(gitUri);
    if (matcher.find()) {
      String hostName = matcher.group(1);
      return hasValidHostName(hostName);
    }
    logger.warn("the uri has invalid host name {}", gitUri);
    return false;
  }

  private boolean hasValidHostName(String hostName) {
    if (hostName == null) {
      return false;
    }
    for (String allowedHost :
        gitRepoReferencedResourceConfiguration.getAllowListedGitRepoHostName()) {
      if (StringUtils.equals(hostName, allowedHost)) {
        return true;
      }
    }
    // AWS Code commit host server is region specific. Here are the list of all the valid git
    // connection endpoint: https://docs.aws.amazon.com/codecommit/latest/userguide/regions.html.
    if (hostName.startsWith("git-codecommit.") && hostName.endsWith(".amazonaws.com")) {
      return true;
    }
    return false;
  }
  /**
   * Validate GCS object name.
   *
   * @param objectName full path to the object in the bucket
   * @throws InvalidNameException
   */
  public static void validateGcsObjectName(String objectName) {
    int nameLength = objectName.getBytes(StandardCharsets.UTF_8).length;
    if (nameLength < 1 || nameLength > 1024) {
      throw new InvalidNameException(
          "bucket object names must contain any sequence of valid Unicode characters, of length 1-1024 bytes when UTF-8 encoded");
    }
    if (objectName.startsWith(ACME_CHALLENGE_PREFIX)) {
      throw new InvalidNameException(
          "bucket object name cannot start with .well-known/acme-challenge/");
    }
    for (String disallowedObjectName : DISALLOWED_OBJECT_NAMES) {
      if (disallowedObjectName.equals(objectName)) {
        throw new InvalidNameException("bucket object name cannot be . or ..");
      }
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

  public static void validateAzureDiskName(String name) {
    Pattern pattern = Pattern.compile("^[a-zA-Z0-9-_]{0,80}$");

    if (!pattern.matcher(name).matches()) {
      logger.warn("Invalid Disk name {}", name);
      throw new InvalidReferenceException(
          "Invalid Azure Disk name specified. See documentation for full specification https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/resource-name-rules.");
    }
  }

  public static void validateAzureBatchPoolId(String id) {
    if (!AZURE_BATCH_POOL_ID_VALIDATION_PATTERN.matcher(id).matches()) {
      logger.warn("Invalid Azure Batch Pool id {}", id);
      throw new InvalidReferenceException(
          "Invalid Azure Batch Pool id specified. Name must be 1 to 64 alphanumeric characters or underscores or dashes.");
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

  public static void validateBqDataTableName(String name) {
    if (StringUtils.isEmpty(name)
        || !BQ_DATATABLE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid data table name %s", name);
      throw new InvalidNameException(
          "Invalid BQ table name specified. Name must be 1-1024 characters, contains Unicode characters in category L"
              + " (letter), M (mark), N (number), Pc (connector, including underscore), Pd (dash), Zs (space)");
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
    if (StringUtils.isEmpty(name) || !RESOURCE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid resource name {}", name);
      throw new InvalidNameException(
          "Invalid resource name specified. Name must be 1 to 1024 alphanumeric characters, underscores, and dashes and must not start with a dash or underscore.");
    }
  }

  public static void validateOptionalResourceName(@Nullable String name) {
    if (name != null && !RESOURCE_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      logger.warn("Invalid resource name {}", name);
      throw new InvalidNameException(
          "Invalid resource name specified. Name must be 1 to 1024 alphanumeric characters, underscores, and dashes and must not start with a dash or underscore.");
    }
  }

  public static void validateResourceDescriptionName(@Nullable String name) {
    if (name != null && name.length() > MAX_RESOURCE_DESCRIPTION_NAME) {
      throw new InvalidNameException(
          "Invalid description specified. Description must be under 2048 characters.");
    }
  }

  public static void validateBatchPoolDisplayName(@Nullable String displayName) {
    if (displayName != null && displayName.length() > MAX_BATCH_POOL_DISPLAY_NAME) {
      throw new InvalidNameException(
          "Invalid display name specified. Display name must be under 1024 characters.");
    }
  }

  public static void validateStorageAccountName(String storageAccountName) {
    if (!AZURE_STORAGE_ACCOUNT_NAME_VALIDATION_PATTERN.matcher(storageAccountName).matches()) {
      logger.warn("Invalid Storage Account name: {}", storageAccountName);
      throw new InvalidReferenceException(
          "Invalid Azure Storage Account name. The name must be 3 to 24 alphanumeric lower case characters.");
    }
  }

  public static void validateStorageContainerName(String storageContainerName) {
    if (!AZURE_STORAGE_CONTAINER_NAME_VALIDATION_PATTERN.matcher(storageContainerName).matches()
        || storageContainerName.contains("--")) {
      logger.warn("Invalid Storage Container name: {}", storageContainerName);
      throw new InvalidReferenceException(
          "Invalid Azure Storage Container name. The name must be 3 to 63 alphanumeric lower case characters "
              + "or dashes, must start and end with a letter or number, and cannot contain consecutive dashes.");
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

  public static void validateAzureRegion(String region) {
    if (!Region.values().stream()
        .map(Region::toString)
        .collect(Collectors.toList())
        .contains(region)) {
      logger.warn("Invalid Azure region {}", region);
      throw new InvalidControlledResourceException("Invalid Azure Region specified.");
    }
  }

  public static void validateGcpRegion(
      TpsApiDispatch tpsApiDispatch, UUID workspaceId, String region) {
    region = GcpUtils.parseRegion(region);

    // Get the list of valid locations for this workspace from TPS. If there are no regional
    // constraints applied to the workspace, TPS should return all available regions.
    List<String> validLocations = tpsApiDispatch.listValidRegions(workspaceId, CloudPlatform.GCP);

    if (validLocations.stream().noneMatch(region::equalsIgnoreCase)) {
      throw new InvalidControlledResourceException(
          String.format("Specified location %s is not allowed by effective policy.", region));
    }
  }

  public static <T> void checkFieldNonNull(@Nullable T fieldValue, String fieldName) {
    if (fieldValue == null) {
      throw new MissingRequiredFieldException(
          String.format("Missing required field '%s' for resource", fieldName));
    }
  }

  public static void validateApiAzureVmCreationParameters(
      ApiAzureVmCreationParameters apiAzureVmCreationParameters) {
    var vmImage = apiAzureVmCreationParameters.getVmImage();
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
    if (StringUtils.isEmpty(vmImage.getUri())
        && (!StringUtils.isEmpty(vmImage.getPublisher())
            && !StringUtils.isEmpty(vmImage.getOffer())
            && !StringUtils.isEmpty(vmImage.getSku())
            && !StringUtils.isEmpty(vmImage.getVersion()))) {
      checkFieldNonNull(apiAzureVmCreationParameters.getVmUser(), "vmUser");
    }
  }

  /**
   * Assert that the cloning instructions specified for a controlled or referenced resource are a
   * valid combination. Intended for use at the api controller level.
   *
   * @param stewardshipType - controlled or referenced
   * @param cloningInstructions - supplied cloning instructions with the API request
   * @throws BadRequestException if the combination is not valid
   */
  public static void validateCloningInstructions(
      StewardshipType stewardshipType, CloningInstructions cloningInstructions) {
    final boolean valid =
        (StewardshipType.CONTROLLED == stewardshipType
                && (CloningInstructions.COPY_NOTHING == cloningInstructions
                    || CloningInstructions.COPY_DEFINITION == cloningInstructions
                    || CloningInstructions.COPY_RESOURCE == cloningInstructions
                    || CloningInstructions.COPY_REFERENCE == cloningInstructions))
            || (StewardshipType.REFERENCED == stewardshipType
                && (CloningInstructions.COPY_NOTHING == cloningInstructions
                    || CloningInstructions.COPY_REFERENCE == cloningInstructions));
    if (!valid) {
      throw new BadRequestException(
          String.format(
              "Cloning Instruction %s is not valid with Stewardship Type %s",
              cloningInstructions.toString(), stewardshipType.toString()));
    }
  }

  /**
   * Validate the terra reserved properties has valid values.
   *
   * @param properties of a resource.
   */
  public static void validateProperties(Map<String, String> properties) {
    if (properties.containsKey(FOLDER_ID_KEY)) {
      try {
        var unused = UUID.fromString(properties.get(FOLDER_ID_KEY));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(
            String.format(
                "Property %s contains an invalid non-UUID format folder id %s.",
                FOLDER_ID_KEY, properties.get(FOLDER_ID_KEY)));
      }
    }
  }
}
