package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledGcsBucketHandler implements WsmResourceHandler {
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
            .bucketName(attributes.getBucketName())
            .common(new ControlledResourceFields(dbResource))
            .build();
    return resource;
  }

  public String generateCloudName(UUID workspaceUuid, String bucketName) {
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    return String.format("%s-%s", bucketName, projectId);
  }
}
