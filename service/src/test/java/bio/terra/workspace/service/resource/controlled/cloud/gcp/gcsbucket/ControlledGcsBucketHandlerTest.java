package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler.MAX_BUCKET_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.BaseUnitTestMockGcpCloudContextService;
import java.io.IOException;
import java.util.UUID;

import bio.terra.workspace.service.workspace.GcpCloudContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ControlledGcsBucketHandlerTest extends BaseUnitTestMockGcpCloudContextService {
  private static final UUID fakeWorkspaceId = UUID.randomUUID();
  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  @BeforeEach
  public void setup() throws IOException {
    when(mockGcpCloudContextService().getRequiredGcpProject(any())).thenReturn(FAKE_PROJECT_ID);
  }

  @Test
  public void generateBucketName() {
    String bucketName = "yuhuyoyo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasStartingDash_removeStartingDash() {
    String bucketName = "-yu-hu-yo-yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yu-hu-yo-yo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasUnderscores_removeUnderscores() {
    String bucketName = "yu_hu_yo_yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasGoogPrefix_removeGoogPrefix() {
    String bucketName = "googyu_hu_yo_yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasGoogle_removeGoogle() {
    String bucketName = "gooyu_hu_googleyo_yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("gooyuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasUppercase_toLowerCase() {
    String bucketName = "YUHUYOYO";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameTooLong_trim() {
    String bucketName =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    int maxNameLength = MAX_BUCKET_NAME_LENGTH;

    assertEquals(maxNameLength, generateCloudName.length());
    assertEquals(generateCloudName, generateCloudName.substring(0, maxNameLength));
  }

  @Test
  public void generateBucketName_bucketNameWithUnsupportedCharacters_removeUnsupportedcharacter() {
    String bucketName = "yuh%uyoy*o";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }
}
