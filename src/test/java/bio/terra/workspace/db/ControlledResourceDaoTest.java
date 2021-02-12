package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ControlledResourceDaoTest extends BaseUnitTest {
  private static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  private static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  private static final SpendProfileId SPEND_PROFILE_ID =
      SpendProfileId.create("22222222-fcf0-4981-bb96-6b8dd634e7c0");
  public static final Workspace WORKSPACE =
      Workspace.builder()
          .workspaceId(WORKSPACE_ID)
          .spendProfileId(Optional.of(SPEND_PROFILE_ID))
          .workspaceStage(WorkspaceStage.MC_WORKSPACE)
          .build();

  @Autowired private ControlledResourceDao controlledResourceDao;
  @Autowired private WorkspaceDao workspaceDao;

  @Test
  public void verifyCreatedGoogleBucketExists() {
    // relational prerequisites
    workspaceDao.createWorkspace(WORKSPACE);

    final ControlledResourceMetadata metadata =
        ControlledResourceMetadata.builder()
            .setWorkspaceId(WORKSPACE_ID)
            .setResourceId(RESOURCE_ID)
            .setIsVisible(true)
            .setOwner("johndoe@biz.dev")
            .build();
    controlledResourceDao.createControlledResource(metadata);

    final Optional<ControlledResourceMetadata> retrieved =
        controlledResourceDao.getControlledResource(RESOURCE_ID);
    assertTrue(retrieved.isPresent());
  }

  @Test
  public void testNoMatchIsEmptyOptional() {
    final Optional<ControlledResourceMetadata> retrieved =
        controlledResourceDao.getControlledResource(UUID.randomUUID());
    assertTrue(retrieved.isEmpty());
  }
}
