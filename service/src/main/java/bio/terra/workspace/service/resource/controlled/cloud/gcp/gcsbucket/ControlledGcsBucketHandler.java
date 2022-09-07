package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.common.base.Preconditions;
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
    ControlledResourceFields commonFields = new ControlledResourceFields(dbResource);
    var resource =
        ControlledGcsBucketResource.builder()
            .bucketName(
                StringUtils.isEmpty(attributes.getBucketName())
                    ? ControlledGcsBucketHandler.getHandler()
                        .generateCloudName(commonFields.getWorkspaceId(), commonFields.getName())
                    : attributes.getBucketName())
            .common(commonFields)
            .build();
    return resource;
  }

  /**
   * Generate GCS bucket cloud name that meets the requirements for a valid name.
   *
   * <p>Bucket names can only contain lowercase letters, numeric characters, dashes (-), underscores
   * (_), and dots (.). Spaces are not allowed. and bucket names must start and end with a number or
   * letter and contain 3-63 characters. In addition, bucket names cannot begin with the "goog"
   * prefix. For details, see https://cloud.google.com/storage/docs/naming-buckets.
   */
  public String generateCloudName(@Nullable UUID workspaceUuid, String bucketName) {
    Preconditions.checkNotNull(workspaceUuid);

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    String generatedName = String.format("%s-%s", bucketName, projectId).toLowerCase();
    generatedName =
        generatedName.length() > MAX_BUCKET_NAME_LENGTH
            ? generatedName.substring(0, MAX_BUCKET_NAME_LENGTH)
            : generatedName;
    generatedName =
        generatedName.replaceAll("google|^goog|[^a-z0-9-_.]+|^[^a-z0-9]+|[^a-z0-9]+$", "");

    return generatedName;
  }
}
