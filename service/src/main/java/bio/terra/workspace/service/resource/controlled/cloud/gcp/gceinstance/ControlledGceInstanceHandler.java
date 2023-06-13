package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ControlledGceInstanceHandler implements WsmResourceHandler {
  private static ControlledGceInstanceHandler theHandler;

  public static ControlledGceInstanceHandler getHandler() {
    return theHandler;
  }

  @PostConstruct
  public void init() {
    theHandler = this;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledGceInstanceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledGceInstanceAttributes.class);

    return ControlledGceInstanceResource.builder()
        .common(new ControlledResourceFields(dbResource))
        .instanceId(attributes.getInstanceId())
        .zone(attributes.getZone())
        .projectId(attributes.getProjectId())
        .build();
  }

  /**
   * Generate GCE instance name that meets the requirements for a valid instance.
   *
   * <p>The resource name must be 1-63 characters long, and comply with RFC1035. Specifically, the
   * name must be 1-63 characters long and match the regular expression [a-z]([-a-z0-9]*[a-z0-9])?
   * which means the first character must be a lowercase letter, and all following characters must
   * be a dash, lowercase letter, or digit, except the last character, which cannot be a dash.
   * https://cloud.google.com/compute/docs/reference/rest/v1/instances/insert
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String instanceName) {
    return GcpUtils.generateInstanceCloudName(workspaceUuid, instanceName);
  }
}
