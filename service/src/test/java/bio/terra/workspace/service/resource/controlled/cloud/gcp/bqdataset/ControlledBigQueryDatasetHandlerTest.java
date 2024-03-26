package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetHandler.MAX_DATASET_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class ControlledBigQueryDatasetHandlerTest extends BaseSpringBootUnitTest {
  @Test
  public void generateDatasetName() {
    String datasetName = "yuhuyoyo";
    String generatedCloudName =
        ControlledBigQueryDatasetHandler.getHandler().generateCloudName((UUID) null, datasetName);

    assertEquals("yuhuyoyo", generatedCloudName);
  }

  @Test
  public void generateDatasetName_datasetNameHasDash_replaceWithUnderscore() {
    String datasetName = "yu-hu-yo-yo";
    String generatedCloudName =
        ControlledBigQueryDatasetHandler.getHandler().generateCloudName((UUID) null, datasetName);

    assertEquals("yu_hu_yo_yo", generatedCloudName);
  }

  @Test
  public void generateDatasetName_datasetNameHasStartingDash_removeStartingDash() {
    String datasetName = "-_yu-hu-yo-yo-_";
    String generatedCloudName =
        ControlledBigQueryDatasetHandler.getHandler().generateCloudName((UUID) null, datasetName);

    assertEquals("yu_hu_yo_yo", generatedCloudName);
  }

  @Test
  public void generateDatasetName_datasetNameHasEndingDash_removeEndingDash() {
    String datasetName = "yu-hu-yo-yo-";
    String generatedCloudName =
        ControlledBigQueryDatasetHandler.getHandler().generateCloudName((UUID) null, datasetName);

    assertEquals("yu_hu_yo_yo", generatedCloudName);
  }

  @Test
  public void generateDatasetName_datasetNameTooLong_trim() {
    String bucketName = RandomStringUtils.random(2000, true, true);
    String generateCloudName =
        ControlledBigQueryDatasetHandler.getHandler().generateCloudName((UUID) null, bucketName);

    int maxNameLength = MAX_DATASET_NAME_LENGTH;

    assertEquals(maxNameLength, generateCloudName.length());
    assertTrue(generateCloudName.startsWith(generateCloudName.substring(0, maxNameLength)));
  }
}
