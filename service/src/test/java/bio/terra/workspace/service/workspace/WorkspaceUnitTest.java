package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WorkspaceUnitTest extends BaseUnitTest {

  @Test
  void workspaceRequiredFields() {
    assertThrows(
        MissingRequiredFieldsException.class, () -> Workspace.builder().displayName("abc").build());

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> Workspace.builder().workspaceUuid(UUID.randomUUID()).build());

    assertThrows(
        MissingRequiredFieldsException.class,
        () -> Workspace.builder().workspaceStage(WorkspaceStage.MC_WORKSPACE).build());
  }
}
