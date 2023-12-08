package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import jakarta.ws.rs.BadRequestException;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledGcsBucketHandler implements WsmResourceHandler {
  protected static final int MAX_BUCKET_NAME_LENGTH = 63;
  private static ControlledGcsBucketHandler theHandler;
  private final GcpCloudContextService gcpCloudContextService;

  @Autowired
  public ControlledGcsBucketHandler(GcpCloudContextService gcpCloudContextService) {
    this.gcpCloudContextService = gcpCloudContextService;
  }

  public static ControlledGcsBucketHandler getHandler() {
    return theHandler;
  }

  @PostConstruct
  public void init() {
    theHandler = this;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledGcsBucketAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledGcsBucketAttributes.class);
    var bucketName =
        StringUtils.isEmpty(attributes.getBucketName())
            ? generateCloudName(dbResource.getWorkspaceId(), dbResource.getName())
            : attributes.getBucketName();
    return new ControlledGcsBucketResource(dbResource, bucketName);
  }

  /**
   * Generate controlled GCS bucket cloud name that meets the requirements for a valid name.
   *
   * <p>Bucket names can only contain lowercase letters, numeric characters, dashes (-), and dots
   * (.). underscores are prohibited for controlled GCS bucket. GCP recommends against underscores
   * in bucket names because DNS hostnames can't have underscores. In particular, Nextflow fails if
   * bucket name has underscore (because bucket name isn't valid DNS hostname.) Spaces are also not
   * allowed. Bucket names must start and end with a number or letter and contain 3-63 characters.
   * In addition, bucket names cannot begin with the "goog" prefix. For details, see
   * https://cloud.google.com/storage/docs/naming-buckets.
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String bucketName) {
    Preconditions.checkNotNull(workspaceUuid);

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);

    String generatedName =
        String.format("%s-%s", bucketName, projectId).toLowerCase().replace("_", "-");

    // The regular expression only allow legal character combinations which start with alphanumeric
    // letter, but not start with "google" or "goog", dash("-") in the string, and alphanumeric
    // letter at the end of the string. It trims any other combinations.
    generatedName = generatedName.replaceAll("google|^goog", "");
    generatedName =
        CharMatcher.inRange('0', '9')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.is('-'))
            .retainFrom(generatedName);
    // Truncate before trimming characters to ensure the name does not end with dash("-").
    generatedName = StringUtils.truncate(generatedName, MAX_BUCKET_NAME_LENGTH);
    // The name cannot start or end with dash("-").
    generatedName = CharMatcher.is('-').trimFrom(generatedName);

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a gcs bucket name from %s, it must contain"
                  + " alphanumerical characters.",
              bucketName));
    }
    return generatedName;
  }
}
