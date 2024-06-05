package bio.terra.workspace.service.resource;

import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.exception.ValidationException;
import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.workspace.app.configuration.external.GitRepoReferencedResourceConfiguration;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.FieldSizeExceededException;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.controlled.exception.RegionNotAllowedException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private final GitRepoReferencedResourceConfiguration gitRepoReferencedResourceConfiguration;

  @Autowired
  public ResourceValidationUtils(
      GitRepoReferencedResourceConfiguration gitRepoReferencedResourceConfiguration) {
    this.gitRepoReferencedResourceConfiguration = gitRepoReferencedResourceConfiguration;
  }

  // General validation functions

  public static <T> void checkFieldNonNull(
      @Nullable T fieldValue, String fieldName, String resourceDescriptor) {
    if (fieldValue == null) {
      throw new MissingRequiredFieldException(
          String.format("Missing required field '%s' for %s", fieldName, resourceDescriptor));
    }
  }

  public static void checkStringNonEmpty(
      @Nullable String fieldValue, String fieldName, String resourceDescriptor) {
    if (StringUtils.isEmpty(fieldValue)) {
      throw new MissingRequiredFieldException(
          String.format("Missing required string '%s' for %s", fieldName, resourceDescriptor));
    }
  }

  // Resource properties

  /**
   * Resource names must be 1-1024 characters, using letters, numbers, dashes, and underscores and
   * must not start with a dash or underscore.
   */
  public static final Pattern RESOURCE_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][-_a-zA-Z0-9]{0,1023}$");

  private static final int MAX_RESOURCE_DESCRIPTION_NAME = 2048;

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

  // Resource operations

  /**
   * Assert that the cloning instructions specified for a controlled or referenced resource are a
   * valid combination. Intended for use at the api controller level.
   *
   * @param stewardshipType - controlled or referenced
   * @param cloningInstructions - supplied cloning instructions with the API request
   * @throws ValidationException if the combination is not valid
   */
  public static void validateCloningInstructions(
      StewardshipType stewardshipType, CloningInstructions cloningInstructions) {
    switch (stewardshipType) {
      case CONTROLLED:
        if (cloningInstructions.isValidForControlledResource()) {
          return;
        }
        break;
      case REFERENCED:
        if (cloningInstructions.isValidForReferencedResource()) {
          return;
        }
    }
    throw new ValidationException(
        String.format(
            "Cloning Instruction %s is not valid with Stewardship Type %s",
            cloningInstructions.toString(), stewardshipType));
  }

  // Region

  public static void validateRegionAgainstPolicy(
      TpsApiDispatch tpsApiDispatch, UUID workspaceUuid, String region, CloudPlatform platform) {
    switch (platform) {
      case GCP ->
          validateRegion(tpsApiDispatch, workspaceUuid, GcpUtils.parseRegion(region), platform);
      case AZURE, AWS -> validateRegion(tpsApiDispatch, workspaceUuid, region, platform);
      case ANY -> {
        // Flexible resources are not stored on the cloud. Thus, they have no region policies.
      }
    }
  }

  /**
   * @return policy violation error messages
   */
  public static List<String> validateExistingResourceRegions(
      UUID workspaceId,
      List<String> validRegions,
      CloudPlatform cloudPlatform,
      ResourceDao resourceDao) {
    List<ControlledResource> existingResources =
        resourceDao.listControlledResources(workspaceId, cloudPlatform);

    return existingResources.stream()
        .filter(
            resource ->
                Optional.ofNullable(resource.getRegion())
                    .map(
                        region ->
                            validRegions.stream()
                                .noneMatch(validRegion -> validRegion.equalsIgnoreCase(region)))
                    .orElse(false))
        .map(
            violation ->
                "Resource %s is in region %s in violation of policy"
                    .formatted(violation.getName(), violation.getRegion()))
        .toList();
  }

  public static void validateRegion(
      TpsApiDispatch tpsApiDispatch, UUID workspaceId, String region, CloudPlatform cloudPlatform) {
    Rethrow.onInterrupted(
        () -> tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE),
        "createPaoIfNotExist");

    // Get the list of valid locations for this workspace from TPS. If there are no regional
    // constraints applied to the workspace, TPS should return all available regions.
    List<String> validLocations =
        Rethrow.onInterrupted(
            () -> tpsApiDispatch.listValidRegions(workspaceId, cloudPlatform), "listValidRegions");

    if (validLocations.stream().noneMatch(region::equalsIgnoreCase)) {
      throw new RegionNotAllowedException(
          String.format(
              "Specified region %s is not allowed by effective policy. Allowed regions are %s",
              region, validLocations));
    }
  }

  // Git

  // Pattern for Git SSH URL. It often but doesn't have to have the .git extension.
  private static final Pattern GIT_SSH_URI_PATTERN = Pattern.compile("git@([.a-z]{0,17}):.*$");

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
    return hostName.startsWith("git-codecommit.") && hostName.endsWith(".amazonaws.com");
  }

  // Flex resources

  private static final int MAX_FLEXIBLE_RESOURCE_DATA_BYTE_SIZE = 5000;

  public static void validateFlexResourceDataSize(@Nullable String decodedData) {
    if (decodedData != null) {
      if (decodedData.getBytes().length > MAX_FLEXIBLE_RESOURCE_DATA_BYTE_SIZE) {
        logger.warn("Exceeded flex resource data limit (at most 5 kilobytes");
        throw new FieldSizeExceededException(
            "Field data is too large. Please limit it to 5 kilobytes.");
      }
    }
  }
}
