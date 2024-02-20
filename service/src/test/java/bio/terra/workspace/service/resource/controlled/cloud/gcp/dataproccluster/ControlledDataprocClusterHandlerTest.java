package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class ControlledDataprocClusterHandlerTest extends BaseSpringBootUnitTest {

  @Test
  public void generateClusterId() {
    String clusterName = "yuhuyoyo";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals("yuhuyoyo", clusterId);
  }

  @Test
  public void generateClusterId_clusterNameHasUnderscore_removeUnderscores() {
    String clusterName = "yu_hu_yo_yo";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals("yuhuyoyo", clusterId);
  }

  @Test
  public void generateClusterId_clusterNameHasStartingUnderscore_removeStartingUnderscores() {
    String clusterName = "___________________yu_hu_yo_yo";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals("yuhuyoyo", clusterId);
  }

  @Test
  public void generateClusterId_clusterNameHasEndingUnderscore_removeEndingUnderscores() {
    String clusterName = "yu_hu_yo_yo__________________";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals("yuhuyoyo", clusterId);
  }

  @Test
  public void generateClusterId_clusterNameHasStartingNumbers_removeStartingNumbers() {
    String clusterName = "1234_______yuhuyoyo";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals("yuhuyoyo", clusterId);
  }

  @Test
  public void generateClusterId_clusterNameHasUppercase_toLowerCase() {
    String clusterName = "YUHUYOYO";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals("yuhuyoyo", clusterId);
  }

  @Test
  public void generateClusterId_clusterNameTooLong_trim() {
    String clusterName =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals(ControlledDataprocClusterHandler.MAX_CLUSTER_NAME_LENGTH, clusterId.length());
    assertEquals(
        clusterId.substring(0, ControlledDataprocClusterHandler.MAX_CLUSTER_NAME_LENGTH),
        clusterId);
  }

  @Test
  public void generateClusterId_clusterNameTooLong_trimDashes() {
    // Generate a name like "aaa-excessText" and ensure it is trimmed to "aaa", not "aaa-", as names
    // may not end in dashes.
    String clusterName =
        StringUtils.repeat("a", ControlledDataprocClusterHandler.MAX_CLUSTER_NAME_LENGTH - 1)
            + "-"
            + "andSomeMoreText";
    String clusterId =
        ControlledDataprocClusterHandler.getHandler().generateCloudName((UUID) null, clusterName);

    assertEquals(ControlledDataprocClusterHandler.MAX_CLUSTER_NAME_LENGTH - 1, clusterId.length());
    assertNotEquals('-', clusterId.charAt(clusterId.length() - 1));
  }
}
