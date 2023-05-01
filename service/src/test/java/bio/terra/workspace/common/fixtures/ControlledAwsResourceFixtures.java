package bio.terra.workspace.common.fixtures;

import bio.terra.stairway.ShortUUID;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/** A series of static objects useful for testing controlled resources. */
public class ControlledAwsResourceFixtures {

  private static final UUID WORKSPACE_ID = UUID.fromString("44444444-fcf0-4981-bb96-6b8dd634e7c0");
  private static final UUID RESOURCE_ID = UUID.fromString("55555555-fcf0-4981-bb96-6b8dd634e7c0");

  public static final String RESOURCE_NAME = "resource-name-" + RESOURCE_ID;
  public static final String RESOURCE_DESCRIPTION = "resource-description for " + RESOURCE_ID;

  // TODO-Dex: move these to test utils
  private static final String DEFAULT_AWS_REGION = "us-east-1";

  public static final ControlledResourceFields DEFAULT_AWS_CONTROLLED_RESOURCE_FIELDS =
      ControlledResourceFields.builder()
          .workspaceUuid(WORKSPACE_ID)
          .resourceId(RESOURCE_ID)
          .name(RESOURCE_NAME)
          .description(RESOURCE_DESCRIPTION)
          .cloningInstructions(CloningInstructions.COPY_RESOURCE)
          .resourceLineage(null)
          .properties(Map.of())
          .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
          .createdDate(null)
          .lastUpdatedByEmail(null)
          .lastUpdatedDate(null)
          .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
          .assignedUser(null)
          .managedBy(ManagedByType.MANAGED_BY_USER)
          .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
          .applicationId(null)
          .region(DEFAULT_AWS_REGION)
          .build();

  public static String uniqueS3FolderName() {
    return "my_s3_folder_" + ShortUUID.get().replace("-", "_");
  }

  /**
   * Make a bigquery builder with defaults filled in NOTE: when using this in a connected test, you
   * MUST overwrite the project id. "my_project" won't work.
   *
   * @return resource builder
   */
  public static ControlledAwsS3StorageFolderResource makeDefaultControlledAwsS3StorageFolder(
      @Nullable UUID workspaceUuid) {
    return new ControlledAwsS3StorageFolderResource.Builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
        .bucketName(DEFAULT_AWS_REGION)
        .prefix(uniqueS3FolderName())
        .build();
  }
}
