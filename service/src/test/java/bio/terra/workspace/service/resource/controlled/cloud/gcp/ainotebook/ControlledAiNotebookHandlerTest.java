package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource.MAX_INSTANCE_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ControlledAiNotebookHandlerTest extends BaseUnitTest {

  @Test
  public void generateInstanceId() {
    String instanceName = "yuhuyoyo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasUnderscore_removeUnderscores() {
    String instanceName = "yu_hu_yo_yo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasStartingUnderscore_removeStartingUnderscores() {
    String instanceName = "___________________yu_hu_yo_yo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasEndingUnderscore_removeEndingUnderscores() {
    String instanceName = "yu_hu_yo_yo__________________";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasStartingNumbers_removeStartingNumbers() {
    String instanceName = "1234_______yuhuyoyo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasUppercase_toLowerCase() {
    String instanceName = "YUHUYOYO";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameTooLong_trim() {
    String instanceName =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo";
    String instanceId =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    int maxNameLength = MAX_INSTANCE_NAME_LENGTH;

    assertEquals(maxNameLength, instanceId.length());
    assertEquals(instanceId.substring(0, maxNameLength), instanceId);
  }
}
