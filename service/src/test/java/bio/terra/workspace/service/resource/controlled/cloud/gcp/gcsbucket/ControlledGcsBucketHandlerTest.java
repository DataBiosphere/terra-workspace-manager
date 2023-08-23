package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler.MAX_BUCKET_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS;

import bio.terra.workspace.common.BaseUnitTestMockGcpCloudContextService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookHandler;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

// TODO: PF-2090 - Spring does not seem to notice that it needs to build a different
//  application context for this test class, even though it inherits a different set
//  of mocks. Doing @DirtiesContext forces a new application context and it is built
//  properly. See the ticket for more details.
@DirtiesContext(classMode = BEFORE_CLASS)
public class ControlledGcsBucketHandlerTest extends BaseUnitTestMockGcpCloudContextService {
  private static final UUID fakeWorkspaceId = UUID.randomUUID();
  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  @BeforeEach
  public void setup() {
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

    assertEquals("yu-hu-yo-yo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasGoogPrefix_removeGoogPrefix() {
    String bucketName = "googyu_hu_yo_yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yu-hu-yo-yo-" + FAKE_PROJECT_ID, generateCloudName);
  }

  @Test
  public void generateBucketName_bucketNameHasGoogle_removeGoogle() {
    String bucketName = "gooyu_hu_googleyo_yo";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("gooyu-hu-yo-yo-" + FAKE_PROJECT_ID, generateCloudName);
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
  public void generateBucketName_bucketNameTooLong_trimDashes() {
    // Generate a name like "aaa-excessText" and ensure it is trimmed to "aaa", not "aaa-", as names
    // may not end in dashes.
    String instanceName =
        StringUtils.repeat("a", MAX_BUCKET_NAME_LENGTH - 1) + "-" + "andSomeMoreText";
    String bucketName =
        ControlledAiNotebookHandler.getHandler().generateCloudName((UUID) null, instanceName);

    assertEquals(MAX_BUCKET_NAME_LENGTH - 1, bucketName.length());
    assertNotEquals('-', bucketName.charAt(bucketName.length() - 1));
  }

  @Test
  public void generateBucketName_bucketNameWithUnsupportedCharacters_removeUnsupportedcharacter() {
    String bucketName = "yuh%uyoy*o";
    String generateCloudName =
        ControlledGcsBucketHandler.getHandler().generateCloudName(fakeWorkspaceId, bucketName);

    assertEquals("yuhuyoyo-" + FAKE_PROJECT_ID, generateCloudName);
  }
}
