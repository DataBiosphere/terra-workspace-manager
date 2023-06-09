package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static bio.terra.workspace.common.testfixtures.ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder;
import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import org.junit.jupiter.api.Test;

public class ControlledAwsS3StorageFolderResourceTest extends BaseAwsUnitTest {

  @Test
  public void validateResourceTest() {
    // success
    ControlledAwsS3StorageFolderResource.Builder resourceBuilder =
        makeAwsS3StorageFolderResourceBuilder("bucket", "prefix");
    assertDoesNotThrow(resourceBuilder::build);

    // missing bucket
    resourceBuilder = makeAwsS3StorageFolderResourceBuilder("", "prefix");
    Exception ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty bucketName");
    assertThat(ex.getMessage(), containsString("bucketName"));

    // missing prefix
    resourceBuilder = makeAwsS3StorageFolderResourceBuilder("bucket", null);
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty prefix");
    assertThat(ex.getMessage(), containsString("prefix"));

    // missing region
    resourceBuilder =
        ControlledAwsS3StorageFolderResource.builder()
            .common(makeDefaultControlledResourceFieldsBuilder().region(null).build())
            .bucketName("bucket")
            .prefix("prefix");
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty region");
    assertThat(ex.getMessage(), containsString("region"));

    // invalid prefix
    resourceBuilder = makeAwsS3StorageFolderResourceBuilder("bucket", "pr%fix");
    ex =
        assertThrows(
            InvalidNameException.class,
            resourceBuilder::build,
            "validation fails with invalid s3 storage folder name");
    assertThat(
        ex.getMessage(),
        containsString(
            "Storage folder names must contain any sequence of valid Unicode characters"));
  }
}
