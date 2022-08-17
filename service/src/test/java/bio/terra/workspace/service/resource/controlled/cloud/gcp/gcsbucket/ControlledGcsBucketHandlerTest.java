package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler.MAX_BUCKET_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ControlledGcsBucketHandlerTest extends BaseUnitTest {
  @MockBean GcpCloudContextService mockGcpCloudContextService;

  private static final UUID fakeWorkSpaceId = UUID.randomUUID();
  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  @BeforeEach
  public void setup() throws IOException {
    when(mockGcpCloudContextService.getRequiredGcpProject(any())).thenReturn(FAKE_PROJECT_ID);
  }

  @Test
  public void generateBucketName() {
    String bucketName = "yuhuyoyo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkSpaceId, bucketName);

    assertTrue(generateCloudName.startsWith("yuhuyoyo-" + FAKE_PROJECT_ID));
  }

  @Test
  public void generateBucketName_bucketNameHasStartingDash_removeStartingDash() {
    String bucketName = "-yu-hu-yo-yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkSpaceId, bucketName);

    assertTrue(generateCloudName.startsWith("yu-hu-yo-yo-" + FAKE_PROJECT_ID));
  }

  @Test
  public void generateBucketName_bucketNameHasUnderscores_removeUnderscores() {
    String bucketName = "yu_hu_yo_yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkSpaceId, bucketName);

    assertTrue(generateCloudName.startsWith("yuhuyoyo-" + FAKE_PROJECT_ID));
  }

  @Test
  public void generateBucketName_bucketNameHasUppercase_toLowerCase() {
    String bucketName = "YUHUYOYO";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkSpaceId, bucketName);

    assertTrue(generateCloudName.startsWith("yuhuyoyo-" + FAKE_PROJECT_ID));
  }

  @Test
  public void generateBucketName_bucketNameTooLong_trim() {
    String bucketName =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkSpaceId, bucketName);

    int maxNameLength = MAX_BUCKET_NAME_LENGTH;

    assertEquals(maxNameLength, generateCloudName.length());
    assertTrue(generateCloudName.startsWith(generateCloudName.substring(0, maxNameLength)));
  }
}
