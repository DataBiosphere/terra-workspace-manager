package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceInfo;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.GoogleBucketUid;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import org.junit.jupiter.api.Test;

public class ControllerValidationUtilsTest extends BaseUnitTest {

  @Test
  public void testCreateDataReferenceTypeMismatchInvalid() {
    DataReferenceInfo bucketReference =
        new DataReferenceInfo().googleBucket(new GoogleBucketUid().bucketName("fake-bucket-id"));
    CreateDataReferenceRequestBody invalidRequest =
        new CreateDataReferenceRequestBody()
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .referenceInfo(bucketReference);
    assertThrows(
        InvalidDataReferenceException.class,
        () -> ControllerValidationUtils.validate(invalidRequest));
  }

  @Test
  public void testCreateDataReferenceNoTypeInvalid() {
    DataReferenceInfo bucketReference =
        new DataReferenceInfo().googleBucket(new GoogleBucketUid().bucketName("fake-bucket-id"));
    CreateDataReferenceRequestBody invalidRequest =
        new CreateDataReferenceRequestBody().referenceInfo(bucketReference);
    assertThrows(
        ValidationException.class, () -> ControllerValidationUtils.validate(invalidRequest));
  }

  @Test
  public void testCreateDataReferenceNoFieldsInvalid() {
    DataReferenceInfo bucketReference = new DataReferenceInfo();
    CreateDataReferenceRequestBody invalidRequest =
        new CreateDataReferenceRequestBody()
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .referenceInfo(bucketReference);
    assertThrows(
        InvalidDataReferenceException.class,
        () -> ControllerValidationUtils.validate(invalidRequest));
  }

  @Test
  public void testCreateDataReferenceMultipleFieldsInvalid() {
    DataReferenceInfo bucketReference =
        new DataReferenceInfo()
            .googleBucket(new GoogleBucketUid().bucketName("fake-bucket-id"))
            .dataRepoSnapshot(
                new DataRepoSnapshot()
                    .instanceName("fake-instance-name")
                    .snapshot("fake-snapshot-name"));
    CreateDataReferenceRequestBody invalidRequest =
        new CreateDataReferenceRequestBody()
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .referenceInfo(bucketReference);
    assertThrows(
        InvalidDataReferenceException.class,
        () -> ControllerValidationUtils.validate(invalidRequest));
  }

  // TODO(PF-404): we're supporting the existing CreateDataReferenceRequest.reference field to
  // migrate clients, but this test should be cleaned up with that field.
  @Test
  public void testCreateDataReferenceDeprecatedReference() {
    DataRepoSnapshot snapshotReference =
        new DataRepoSnapshot().snapshot("snapshot-name").instanceName("instance-name");
    CreateDataReferenceRequestBody deprecatedRequest =
        new CreateDataReferenceRequestBody()
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(snapshotReference);
    ControllerValidationUtils.validate(deprecatedRequest);
  }
}
