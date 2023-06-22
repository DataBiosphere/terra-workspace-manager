package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.GcpUtils;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class ControlledGceInstanceHandlerTest extends BaseUnitTest {

  @Test
  public void generateInstanceId() {
    String instanceName = "yuhuyoyo";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasUnderscore_removeUnderscores() {
    String instanceName = "yu_hu_yo_yo";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasStartingUnderscore_removeStartingUnderscores() {
    String instanceName = "___________________yu_hu_yo_yo";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasEndingUnderscore_removeEndingUnderscores() {
    String instanceName = "yu_hu_yo_yo__________________";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasStartingNumbers_removeStartingNumbers() {
    String instanceName = "1234_______yuhuyoyo";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameHasUppercase_toLowerCase() {
    String instanceName = "YUHUYOYO";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals("yuhuyoyo", instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameTooLong_trim() {
    String instanceName =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals(GcpUtils.MAX_INSTANCE_NAME_LENGTH, instanceId.length());
    assertEquals(instanceId.substring(0, GcpUtils.MAX_INSTANCE_NAME_LENGTH), instanceId);
  }

  @Test
  public void generateInstanceId_instanceNameTooLong_trimDashes() {
    // Generate a name like "aaa-excessText" and ensure it is trimmed to "aaa", not "aaa-", as names
    // may not end in dashes.
    String instanceName =
        StringUtils.repeat("a", GcpUtils.MAX_INSTANCE_NAME_LENGTH - 1) + "-" + "andSomeMoreText";
    String instanceId =
        ControlledGceInstanceHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals(GcpUtils.MAX_INSTANCE_NAME_LENGTH - 1, instanceId.length());
    assertNotEquals('-', instanceId.charAt(instanceId.length() - 1));
  }
}
