package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.common.base.CharMatcher;
import jakarta.ws.rs.BadRequestException;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledBigQueryDatasetHandler implements WsmResourceHandler {

  protected static final int MAX_DATASET_NAME_LENGTH = 1024;
  private static ControlledBigQueryDatasetHandler theHandler;
  private final GcpCloudContextService gcpCloudContextService;

  @Autowired
  public ControlledBigQueryDatasetHandler(GcpCloudContextService gcpCloudContextService) {
    this.gcpCloudContextService = gcpCloudContextService;
  }

  public static ControlledBigQueryDatasetHandler getHandler() {
    return theHandler;
  }

  @PostConstruct
  public void init() {
    theHandler = this;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    // Old version of attributes do not have project id, so in that case we look it up
    ControlledBigQueryDatasetAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledBigQueryDatasetAttributes.class);
    String projectId =
        Optional.ofNullable(attributes.getProjectId())
            .orElse(gcpCloudContextService.getRequiredGcpProject(dbResource.getWorkspaceId()));

    return ControlledBigQueryDatasetResource.builder()
        .datasetName(
            ControlledBigQueryDatasetHandler.getHandler()
                .generateCloudName(dbResource.getWorkspaceId(), attributes.getDatasetName()))
        .projectId(projectId)
        .defaultTableLifetime(attributes.getDefaultTableLifetime())
        .defaultPartitionLifetime(attributes.getDefaultPartitionLifetime())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }

  /**
   * Generate big query dataset cloud name that meets the requirements for a valid name.
   *
   * <p>Big query dataset names can only contain letters, numeric characters, and underscores (_) up
   * to 1024 characters. Spaces are not allowed. For details, see
   * https://cloud.google.com/bigquery/docs/datasets#dataset-naming.
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String bqDatasetName) {
    String generatedName = bqDatasetName.replace("-", "_");

    // The regular expression only allow legal character combinations which start with alphanumeric
    //  letter, alphanumeric letter and underscore ("_") in the string, and alphanumeric letter at
    //  the end of the string. It trims any other combinations.
    generatedName =
        CharMatcher.inRange('0', '9')
            .or(CharMatcher.inRange('A', 'z'))
            .or(CharMatcher.is('_'))
            .retainFrom(generatedName);
    // The name cannot start or end with dash("-")
    generatedName = CharMatcher.is('_').trimFrom(generatedName);

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid big query dataset name from %s, it must contain"
                  + " alphanumerical characters.",
              bqDatasetName));
    }
    return StringUtils.truncate(generatedName, MAX_DATASET_NAME_LENGTH);
  }
}
