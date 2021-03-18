package scripts.testscripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcsBucketRequestBody;
import bio.terra.workspace.model.CreatedControlledGcsBucket;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.GcsBucketCreationParameters;
import bio.terra.workspace.model.GcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcsBucketLifecycle;
import bio.terra.workspace.model.GcsBucketLifecycleRule;
import bio.terra.workspace.model.GcsBucketLifecycleRuleAction;
import bio.terra.workspace.model.GcsBucketLifecycleRuleActionType;
import bio.terra.workspace.model.GcsBucketLifecycleRuleCondition;
import bio.terra.workspace.model.GcsBucketStoredAttributes;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ReferenceTypeEnum;
import org.apache.http.HttpStatus;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceFixtureTestScriptBase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ControlledGcsBucket extends WorkspaceFixtureTestScriptBase {
  private static final GcsBucketLifecycleRule LIFECYCLE_RULE_1 =
          new GcsBucketLifecycleRule()
                  .action(
                          new GcsBucketLifecycleRuleAction()
                                  .type(
                                          GcsBucketLifecycleRuleActionType
                                                  .DELETE)) // no storage class require for delete actions
                  .condition(
                          new GcsBucketLifecycleRuleCondition()
                                  .age(64)
                                  .live(true)
                                  .addMatchesStorageClassItem(GcsBucketDefaultStorageClass.ARCHIVE)
                                  .numNewerVersions(2));

  private static final GcsBucketLifecycleRule LIFECYCLE_RULE_2 =
          new GcsBucketLifecycleRule()
                  .action(
                          new GcsBucketLifecycleRuleAction()
                                  .storageClass(GcsBucketDefaultStorageClass.NEARLINE)
                                  .type(GcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
                  .condition(
                          new GcsBucketLifecycleRuleCondition()
                                  .createdBefore(LocalDate.of(2017, 2, 18))
                                  .addMatchesStorageClassItem(GcsBucketDefaultStorageClass.STANDARD));

  // list must not be immutable if deserialization is to work
  static final List<GcsBucketLifecycleRule> LIFECYCLE_RULES =
          new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));

  private static final String BUCKET_LOCATION = "US-CENTRAL1";
  private static final String BUCKET_PREFIX = "wsmtestrun-";

  private TestUserSpecification reader;
  private String bucketName;
-
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) {
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat("There must be at least two test users defined for this test.",
            testUsers != null && testUsers.size() > 1);
    this.reader = testUsers.get(1);

    this.bucketName = BUCKET_PREFIX + UUID.randomUUID().toString();
  }

  @Override
  public void doUserJourney(TestUserSpecification testUsers, WorkspaceApi workspaceApi)
      throws ApiException {

    // Create a user-shared controlled GCS bucket
    ControlledGcpResourceApi resourceApi = ClientTestUtils.getControlledGpcResourceClient(testUsers., server);
    var creationParameters =
            new GcsBucketCreationParameters()
                    .name(bucketName)
                    .location(BUCKET_LOCATION)
                    .defaultStorageClass(GcsBucketDefaultStorageClass.STANDARD)
                    .lifecycle(new GcsBucketLifecycle().rules(LIFECYCLE_RULES));

    var commonParameters =
            new ControlledResourceCommonFields()
            .name(bucketName)
            .cloningInstructions(CloningInstructionsEnum.)
            .jobControl(new JobControl()
            .id(UUID.randomUUID().toString()));

    var body = new CreateControlledGcsBucketRequestBody()
    .gcsBucket(creationParameters)
    .common(commonParameters);

    CreatedControlledGcsBucket bucket = resourceApi.createBucket(body, getWorkspaceId());
    assertThat(bucket.getGcpBucket().getBucketName(), equalTo(bucketName));

    // Retrieve the bucket resource
    GcsBucketStoredAttributes gotBucket = resourceApi.getBucket(getWorkspaceId(), bucket.getResourceId());
    assertThat(gotBucket.getBucketName(), equalTo(bucket.getGcpBucket().getBucketName()));

    // TODO: Check access:
      // - writer can add the file
      // - writer can read the file
      // - reader can read the file
      // - reader cannot write a file
      // - reader cannot delete the bucket

      // Delete bucket
    resourceApi.

    // verify it's not there anymore
    DataReferenceDescription dataReferenceDescription = null;
    try {
       dataReferenceDescription = workspaceApi.getDataReference(getWorkspaceId(), newReference.getReferenceId());
    } catch (ApiException expected) {
      assertThat(expected.getCode(), equalTo(HttpStatus.SC_NOT_FOUND));
    }
    assertThat(dataReferenceDescription, equalTo(null));
    assertThat(workspaceApi.getApiClient().getStatusCode(), equalTo(HttpStatus.SC_NOT_FOUND));
  }

  private void assertDataReferenceDescription(DataReferenceDescription dataReferenceDescription, String dataReferenceName) {
    assertThat(dataReferenceDescription.getCloningInstructions(), equalTo(CloningInstructionsEnum.REFERENCE));
    assertThat(dataReferenceDescription.getName(), equalTo(dataReferenceName));
    assertThat(dataReferenceDescription.getReferenceType(), equalTo(ReferenceTypeEnum.DATA_REPO_SNAPSHOT));
    assertThat(dataReferenceDescription.getReference().getSnapshot(), equalTo(
        ClientTestUtils.TEST_SNAPSHOT));
    assertThat(dataReferenceDescription.getReference().getInstanceName(), equalTo(
        ClientTestUtils.TERRA_DATA_REPO_INSTANCE));
  }
}
