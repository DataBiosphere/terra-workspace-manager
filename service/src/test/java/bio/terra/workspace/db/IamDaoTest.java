package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.db.IamDao.PocUser;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IamDaoTest extends BaseAzureTest {
  @Autowired IamDao iamDao;

  @Test
  public void basicTest() {
    UUID workspaceId = UUID.randomUUID();
    PocUser fred =
        new PocUser().userId(UUID.randomUUID().toString()).email("fred@notonmicrosoft.com");

    PocUser wilma =
        new PocUser().userId(UUID.randomUUID().toString()).email("wilma@notonmicrosoft.com");

    PocUser betty =
        new PocUser().userId(UUID.randomUUID().toString()).email("betty@notonmicrosoft.com");

    PocUser barney =
        new PocUser().userId(UUID.randomUUID().toString()).email("barney@notonmicrosoft.com");

    // fred creates a workspace. Note no resource creation operation.
    iamDao.grantRole(workspaceId, WsmIamRole.OWNER, fred);
    // grant writer to wilma
    iamDao.grantRole(workspaceId, WsmIamRole.WRITER, wilma);
    // grant reader to betty
    iamDao.grantRole(workspaceId, WsmIamRole.READER, betty);

    assertTrue(iamDao.roleCheck(workspaceId, WsmIamRole.OWNER, fred.getUserId()), "fred owner");
    assertTrue(iamDao.roleCheck(workspaceId, WsmIamRole.WRITER, wilma.getUserId()), "wilma writer");
    assertTrue(iamDao.roleCheck(workspaceId, WsmIamRole.READER, betty.getUserId()), "betty reader");
    assertFalse(
        iamDao.roleCheck(workspaceId, WsmIamRole.OWNER, barney.getUserId()), "barney not owner");
    assertFalse(
        iamDao.roleCheck(workspaceId, WsmIamRole.OWNER, betty.getUserId()), "betty not owner");

    // Duplicate grants should not cause errors
    iamDao.grantRole(workspaceId, WsmIamRole.OWNER, fred);
    iamDao.grantRole(workspaceId, WsmIamRole.WRITER, wilma);
    iamDao.grantRole(workspaceId, WsmIamRole.READER, wilma);

    // Give more than one role
    iamDao.grantRole(workspaceId, WsmIamRole.WRITER, barney);
    iamDao.grantRole(workspaceId, WsmIamRole.READER, barney);

    String barneyId = barney.getUserId();
    assertTrue(iamDao.roleCheck(workspaceId, WsmIamRole.WRITER, barneyId), "barney writer");
    assertTrue(iamDao.roleCheck(workspaceId, WsmIamRole.READER, barneyId), "barney reader");
    assertFalse(iamDao.roleCheck(workspaceId, WsmIamRole.OWNER, barneyId), "barney not owner");

    // Revoke does not error if you revoke a role you do not have
    iamDao.revokeRole(workspaceId, WsmIamRole.WRITER, barneyId);
    iamDao.revokeRole(workspaceId, WsmIamRole.WRITER, barneyId);
    iamDao.revokeRole(workspaceId, WsmIamRole.READER, barneyId);
    iamDao.revokeRole(workspaceId, WsmIamRole.OWNER, barneyId);
    assertFalse(iamDao.roleCheck(workspaceId, WsmIamRole.WRITER, barneyId), "barney not writer");
    assertFalse(iamDao.roleCheck(workspaceId, WsmIamRole.READER, barneyId), "barney not reader");
    assertFalse(iamDao.roleCheck(workspaceId, WsmIamRole.OWNER, barneyId), "barney not owner");

    // fred deletes the workspace
    iamDao.deleteWorkspace(workspaceId);

    // Make sure all of the roles went away
    assertFalse(
        iamDao.roleCheck(workspaceId, WsmIamRole.OWNER, fred.getUserId()), "fred not owner");
    assertFalse(
        iamDao.roleCheck(workspaceId, WsmIamRole.WRITER, wilma.getUserId()), "wilma not writer");
    assertFalse(
        iamDao.roleCheck(workspaceId, WsmIamRole.READER, betty.getUserId()), "betty not reader");
  }

  @Test
  public void badUserTest() {
    UUID workspaceId = UUID.randomUUID();

    PocUser goodBarney =
        new PocUser().userId(UUID.randomUUID().toString()).email("barney@notonmicrosoft.com");
    PocUser badBarney =
        new PocUser().userId(goodBarney.getUserId()).email("badbarney@notonmicrosoft.com");

    iamDao.grantRole(workspaceId, WsmIamRole.OWNER, goodBarney);
    assertThrows(
        IllegalStateException.class,
        () -> iamDao.grantRole(workspaceId, WsmIamRole.OWNER, badBarney));
  }
}
