package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.common.base.Preconditions;
import java.util.UUID;
import javax.annotation.PostConstruct;
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
    return new ControlledGcsBucketResource(dbResource);
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
  public String generateCloudName(UUID workspaceUuid, String bucketName) {
    Preconditions.checkNotNull(workspaceUuid);

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    String generatedName = String.format("%s-%s", bucketName, projectId).toLowerCase();
    generatedName =
        generatedName.length() > MAX_BUCKET_NAME_LENGTH
            ? generatedName.substring(0, MAX_BUCKET_NAME_LENGTH)
            : generatedName;

    /**
     * The regular expression only allow legal character combinations which start with alphanumeric
     * letter, but not start with "google" or "goog", dash("-") in the string, and alphanumeric
     * letter at the end of the string. It trims any other combinations.
     */
    generatedName =
        generatedName.replaceAll("google|^goog|[^a-z0-9-.]+|^[^a-z0-9]+|[^a-z0-9]+$", "");

    return generatedName;
  }
}
