package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ControlledResourceDaoTest extends BaseUnitTest {
  private static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  private static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  private static final SpendProfileId SPEND_PROFILE_ID =
      SpendProfileId.create("22222222-fcf0-4981-bb96-6b8dd634e7c0");
  public static final Workspace WORKSPACE =
      Workspace.builder()
          .workspaceId(WORKSPACE_ID)
          .spendProfileId(SPEND_PROFILE_ID)
          .workspaceStage(WorkspaceStage.MC_WORKSPACE)
          .build();
  public static final ControlledResourceDbModel DB_MODEL =
      ControlledResourceDbModel.builder()
          .setWorkspaceId(WORKSPACE_ID)
          .setResourceId(RESOURCE_ID)
          .setOwner("johndoe@biz.dev")
          .build();

  @Autowired private ControlledResourceDao controlledResourceDao;
  @Autowired private WorkspaceDao workspaceDao;

  @Test
  public void verifyCreatedGoogleBucketExists() {
    // relational prerequisites
    workspaceDao.createWorkspace(WORKSPACE);

    controlledResourceDao.createControlledResource(DB_MODEL);

    final Optional<ControlledResourceDbModel> retrieved =
        controlledResourceDao.getControlledResource(RESOURCE_ID);
    assertTrue(retrieved.isPresent());
  }

  @Test
  public void testNoMatchIsEmptyOptional() {
    final Optional<ControlledResourceDbModel> retrieved =
        controlledResourceDao.getControlledResource(UUID.randomUUID());
    assertTrue(retrieved.isEmpty());
  }

  @Test
  public void testDelete() {
    final boolean deleted = controlledResourceDao.deleteControlledResource(RESOURCE_ID);
    assertFalse(deleted);

    // relational prerequisites
    workspaceDao.createWorkspace(WORKSPACE);

    controlledResourceDao.createControlledResource(DB_MODEL);

    final Optional<ControlledResourceDbModel> retrieved =
        controlledResourceDao.getControlledResource(RESOURCE_ID);
    assertTrue(retrieved.isPresent());

    final boolean deleted2 = controlledResourceDao.deleteControlledResource(RESOURCE_ID);
    assertTrue(deleted2);
  }
}
