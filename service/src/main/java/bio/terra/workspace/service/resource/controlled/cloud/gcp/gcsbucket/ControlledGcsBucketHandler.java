package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledGcsBucketHandler implements WsmResourceHandler {

  private static final int MAX_BUCKET_NAME_LENGTH = 63;
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
    var resource =
        ControlledGcsBucketResource.builder()
            .common(new ControlledResourceFields(dbResource))
            .bucketName(attributes.getBucketName())
            .build();
    return resource;
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String bucketName) {
    if (workspaceUuid == null) {
      return bucketName;
    }
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    String generatedbucketCloudName = String.format("%s-%s", bucketName, projectId).toLowerCase();
    generatedbucketCloudName =
        generatedbucketCloudName.length() > MAX_BUCKET_NAME_LENGTH
            ? generatedbucketCloudName.substring(0, MAX_BUCKET_NAME_LENGTH)
            : generatedbucketCloudName;
    generatedbucketCloudName =
        generatedbucketCloudName.endsWith("-")
            ? generatedbucketCloudName.substring(0, generatedbucketCloudName.length() - 1)
            : generatedbucketCloudName;
    generatedbucketCloudName =
        generatedbucketCloudName.startsWith("-")
            ? generatedbucketCloudName.substring(1)
            : generatedbucketCloudName;
    return generatedbucketCloudName;
  }
}
