package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledDataprocClusterHandler implements WsmResourceHandler {

  private static ControlledDataprocClusterHandler theHandler;
  private final GcpCloudContextService gcpCloudContextService;
  @VisibleForTesting public static final int MAX_CLUSTER_NAME_LENGTH = 52;

  @Autowired
  public ControlledDataprocClusterHandler(GcpCloudContextService gcpCloudContextService) {
    this.gcpCloudContextService = gcpCloudContextService;
  }

  public static ControlledDataprocClusterHandler getHandler() {
    return theHandler;
  }

  @PostConstruct
  public void init() {
    theHandler = this;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledDataprocClusterAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledDataprocClusterAttributes.class);

    return ControlledDataprocClusterResource.builder()
        .common(new ControlledResourceFields(dbResource))
        .clusterId(attributes.getClusterId())
        .region(attributes.getRegion())
        .projectId(attributes.getProjectId())
        .build();
  }

  /**
   * Generate Dataproc cluster name.
   *
   * <p>The cluster name must start with a lowercase letter followed by up to 51 lowercase letters,
   * numbers, and hyphens, and cannot end with a hyphen. See
   * https://cloud.google.com/dataproc/docs/guides/create-cluster#creating_a_cloud_dataproc_cluster.
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String clusterName) {
    try {
      // Initially generate an instance name using the shared gce instance name generator.
      String generatedName = GcpUtils.generateInstanceCloudName(workspaceUuid, clusterName);
      // Trimming the generated name to the max allowed length of a Dataproc cluster name.
      generatedName = StringUtils.truncate(generatedName, MAX_CLUSTER_NAME_LENGTH);
      // The name cannot end with dash("-").
      return CharMatcher.is('-').trimTrailingFrom(generatedName);
    } catch (Exception e) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid cluster name from %s, it must only contain alphanumerical characters of length 1 - "
                  + MAX_CLUSTER_NAME_LENGTH
                  + ".",
              clusterName));
    }
  }
}
