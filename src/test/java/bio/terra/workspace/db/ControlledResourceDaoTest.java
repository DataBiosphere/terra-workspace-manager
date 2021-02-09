package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ControlledResourceDaoTest extends BaseUnitTest {
  private static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  private static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  private static final SpendProfileId SPEND_PROFILE_ID =
      SpendProfileId.create("22222222-fcf0-4981-bb96-6b8dd634e7c0");
  private static final GoogleBucketCreationParameters BUCKET_CREATION_PARAMETERS =
      new GoogleBucketCreationParameters()
          .name("my-bucket-name")
          .location("us-central1")
          .defaultStorageClass(GoogleBucketDefaultStorageClass.STANDARD);
  @Autowired private ControlledResourceDao controlledResourceDao;
  @Autowired private WorkspaceDao workspaceDao;
  public static final Workspace WORKSPACE =
      Workspace.builder()
          .workspaceId(WORKSPACE_ID)
          .spendProfileId(Optional.of(SPEND_PROFILE_ID))
          .workspaceStage(WorkspaceStage.MC_WORKSPACE)
          .build();

  @BeforeEach
  public void setup() {
    workspaceDao.createWorkspace(WORKSPACE);
  }

  @Test
  public void verifyCreatedGoogleBucketExists() {
    final ControlledResourceMetadata metadata =
        ControlledResourceMetadata.builder()
            .workspaceId(WORKSPACE_ID)
            .resourceId(RESOURCE_ID)
            .isVisible(true)
            .owner("johndoe@biz.dev")
            .build();
    controlledResourceDao.createControlledResource(metadata);

    final Optional<ControlledResourceMetadata> retrieved =
        controlledResourceDao.getControlledResource(WORKSPACE_ID, RESOURCE_ID);
    assertTrue(retrieved.isPresent());
  }
}
