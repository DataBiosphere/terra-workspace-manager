package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource.MAX_INSTANCE_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

public class ControlledAiNotebookHandlerTest extends BaseUnitTest {

  @Test
  public void generateInstanceId() {
    String instanceName = "yuhuyoyo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    assertTrue(instanceId.equals("yuhuyoyo"));
  }

  @Test
  public void generateInstanceId_userIdHasUnderscore_removeUnderscores() {
    String instanceName = "yu_hu_yo_yo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    assertTrue(instanceId.equals("yuhuyoyo"));
  }

  @Test
  public void generateInstanceId_userIdHasStartingUnderscore_removeStartingUnderscores() {
    String instanceName = "_yu_hu_yo_yo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    assertTrue(instanceId.equals("yuhuyoyo"));
  }

  @Test
  public void generateInstanceId_userIdHasEndingUnderscore_removeEndingUnderscores() {
    String instanceName = "yu_hu_yo_yo_";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    assertTrue(instanceId.equals("yuhuyoyo"));
  }

  @Test
  public void generateInstanceId_userIdHasStartingNumbers_removeStartingNumbers() {
    String instanceName = "1234_yuhuyoyo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    assertTrue(instanceId.equals("yuhuyoyo"));
  }

  @Test
  public void generateInstanceId_userIdHasUppercase_toLowerCase() {
    String instanceName = "YUHUYOYO";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    assertTrue(instanceId.equals("yuhuyoyo"));
  }

  @Test
  public void generateInstanceId_userIdTooLong_trim() {
    String instanceName =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName(null, instanceName);

    int maxNameLength = MAX_INSTANCE_NAME_LENGTH;

    assertEquals(maxNameLength, instanceId.length());
    assertTrue(instanceId.equals(instanceId.substring(0, maxNameLength)));
  }
}
