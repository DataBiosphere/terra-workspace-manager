package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import org.junit.jupiter.api.Test;

public class ControlledAwsS3StorageFolderResourceTest extends BaseAwsSpringBootUnitTest {

  @Test
  void validateResourceTest() {
    // success
    assertDoesNotThrow(
        () ->
            ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                    WORKSPACE_ID, "resource", "bucket", "prefix")
                .build());

    // missing bucket
    Exception ex =
        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                        WORKSPACE_ID, "resource", "", "prefix")
                    .build(),
            "validation fails with empty bucketName");
    assertThat(ex.getMessage(), containsString("bucketName"));

    // missing prefix
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                        WORKSPACE_ID, "resource", "bucket", null)
                    .build(),
            "validation fails with empty prefix");
    assertThat(ex.getMessage(), containsString("prefix"));

    // missing region
    ControlledAwsS3StorageFolderResource.Builder resourceBuilder =
        ControlledAwsS3StorageFolderResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .region(null)
                    .build())
            .bucketName("bucket")
            .prefix("prefix");
    ex =
        assertThrows(
            MissingRequiredFieldException.class,
            resourceBuilder::build,
            "validation fails with empty region");
    assertThat(ex.getMessage(), containsString("region"));

    // invalid prefix
    ex =
        assertThrows(
            InvalidNameException.class,
            () ->
                ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                        WORKSPACE_ID, "resource", "bucket", "pr%fix")
                    .build(),
            "validation fails with invalid s3 storage folder name");
    assertThat(
        ex.getMessage(),
        containsString(
            "Storage folder names must contain any sequence of valid Unicode characters"));
  }
}
