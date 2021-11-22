package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static scripts.utils.ClientTestUtils.getOrFail;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_NAME;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;
import static scripts.utils.ResourceMaker.makeControlledBigQueryDatasetUserPrivate;
import static scripts.utils.ResourceMaker.makeControlledBigQueryDatasetUserShared;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserPrivate;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.CloneResourceResult;
import bio.terra.workspace.model.CloneWorkspaceRequest;
import bio.terra.workspace.model.CloneWorkspaceResult;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.ResourceCloneDetails;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.WorkspaceDescription;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.ResourceModifier;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneWorkspace extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneWorkspace.class);
  private ControlledGcpResourceApi cloningUserResourceApi;
  private CreatedControlledGcpGcsBucket copyDefinitionSourceBucket;
  private CreatedControlledGcpGcsBucket privateSourceBucket;
  private CreatedControlledGcpGcsBucket sharedCopyNothingSourceBucket;
  private CreatedControlledGcpGcsBucket sharedSourceBucket;
  private GcpBigQueryDatasetResource sourceDatasetReference;
  private GcpBigQueryDataTableResource sourceDataTableReference;
  private GcpBigQueryDatasetResource copyDefinitionDataset;
  private GcpBigQueryDatasetResource copyResourceDataset;
  private GcpBigQueryDatasetResource privateDataset;
  private GcpGcsBucketResource sourceBucketReference;
  private String copyDefinitionDatasetName;
  private String copyResourceDatasetName;
  private String nameSuffix;
  private String privateDatasetName;
  private String sharedBucketSourceResourceName;
  private String sourceProjectId;
  private TestUserSpecification cloningUser;
  private UUID destinationWorkspaceId;
  private WorkspaceApi cloningUserWorkspaceApi;

  // Roles to grant user on private resource
  private static final ImmutableList<ControlledResourceIamRole> PRIVATE_ROLES =
      ImmutableList.of(ControlledResourceIamRole.WRITER, ControlledResourceIamRole.EDITOR);

  @Override
  protected void doSetup(
      List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    // set up 2 users
    assertThat(testUsers, hasSize(2));
    // user creating the source resources
    final TestUserSpecification sourceOwnerUser = testUsers.get(0);
    // user cloning the workspace
    cloningUser = testUsers.get(1);

    // Build source GCP project in main test workspace
    sourceProjectId =
        CloudContextMaker.createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    // add cloning user as reader on the workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // give users resource APIs
    final ControlledGcpResourceApi sourceOwnerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);

    // Create a GCS bucket with data
    // create source bucket with COPY_RESOURCE - should clone fine
    nameSuffix = UUID.randomUUID().toString();
    sharedBucketSourceResourceName = RESOURCE_PREFIX + nameSuffix;
    sharedSourceBucket =
        makeControlledGcsBucketUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            sharedBucketSourceResourceName,
            CloningInstructionsEnum.RESOURCE);
    ResourceModifier.addFileToBucket(sharedSourceBucket, sourceOwnerUser, sourceProjectId);

    // create a private GCS bucket, which the non-creating user can't clone
    final PrivateResourceIamRoles privateRoles = new PrivateResourceIamRoles();
    privateRoles.addAll(PRIVATE_ROLES);
    privateSourceBucket =
        makeControlledGcsBucketUserPrivate(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            UUID.randomUUID().toString(),
            sourceOwnerUser.userEmail,
            privateRoles,
            CloningInstructionsEnum.RESOURCE);
    ResourceModifier.addFileToBucket(privateSourceBucket, sourceOwnerUser, sourceProjectId);

    // create a GCS bucket with data and COPY_NOTHING instruction
    sharedCopyNothingSourceBucket =
        makeControlledGcsBucketUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            UUID.randomUUID().toString(),
            CloningInstructionsEnum.NOTHING);
    ResourceModifier.addFileToBucket(
        sharedCopyNothingSourceBucket, sourceOwnerUser, sourceProjectId);

    // create a GCS bucket with data and COPY_DEFINITION
    copyDefinitionSourceBucket =
        makeControlledGcsBucketUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            UUID.randomUUID().toString(),
            CloningInstructionsEnum.DEFINITION);
    ResourceModifier.addFileToBucket(copyDefinitionSourceBucket, sourceOwnerUser, sourceProjectId);

    // Create a BigQuery Dataset with tables and COPY_DEFINITION
    copyDefinitionDatasetName = "copy_definition_" + nameSuffix.replace('-', '_');
    copyDefinitionDataset =
        makeControlledBigQueryDatasetUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            copyDefinitionDatasetName,
            CloningInstructionsEnum.DEFINITION);
    ResourceModifier.populateBigQueryDataset(
        copyDefinitionDataset, sourceOwnerUser, sourceProjectId);

    // Create a BigQuery dataset with tables and COPY_RESOURCE
    copyResourceDatasetName = "copy_resource_dataset";
    copyResourceDataset =
        makeControlledBigQueryDatasetUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            copyResourceDatasetName,
            CloningInstructionsEnum.RESOURCE);
    ResourceModifier.populateBigQueryDataset(copyResourceDataset, sourceOwnerUser, sourceProjectId);

    // Create a private BQ dataset
    privateDatasetName = "private_dataset";
    privateDataset =
        makeControlledBigQueryDatasetUserPrivate(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            privateDatasetName,
            sourceOwnerUser.userEmail,
            privateRoles,
            CloningInstructionsEnum.RESOURCE);

    // Create reference to GCS bucket with COPY_REFERENCE
    final ApiClient apiClient = ClientTestUtils.getClientForTestUser(sourceOwnerUser, server);
    final var referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);
    final String bucketReferenceName = RandomStringUtils.random(16, true, false);

    sourceBucketReference =
        ResourceMaker.makeGcsBucketReference(
            referencedGcpResourceApi,
            getWorkspaceId(),
            bucketReferenceName,
            CloningInstructionsEnum.REFERENCE);

    // create reference to BQ dataset with COPY_NOTHING
    sourceDatasetReference =
        ResourceMaker.makeBigQueryDatasetReference(
            referencedGcpResourceApi, getWorkspaceId(), "dataset_resource_1");
    sourceDataTableReference =
        ResourceMaker.makeBigQueryDataTableReference(
            referencedGcpResourceApi, getWorkspaceId(), "datatable_resource_1");
  }

  @Override
  protected void doUserJourney(
      TestUserSpecification sourceOwnerUser, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    // As reader user, clone the workspace
    // Get a new workspace API for the reader
    cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);
    final CloneWorkspaceRequest cloneWorkspaceRequest =
        new CloneWorkspaceRequest()
            .displayName("Cloned Workspace")
            .description("A clone of workspace " + getWorkspaceId().toString())
            .spendProfile(getSpendProfileId()) // TODO- use a different one if available
            .location("us-central1");
    CloneWorkspaceResult cloneResult =
        cloningUserWorkspaceApi.cloneWorkspace(cloneWorkspaceRequest, getWorkspaceId());

    final String jobId = cloneResult.getJobReport().getId();
    cloneResult =
        ClientTestUtils.pollWhileRunning(
            cloneResult,
            () -> cloningUserWorkspaceApi.getCloneWorkspaceResult(getWorkspaceId(), jobId),
            CloneWorkspaceResult::getJobReport,
            Duration.ofSeconds(10));
    logger.info("Clone result: {}", cloneResult);
    ClientTestUtils.assertJobSuccess(
        "Clone Workspace", cloneResult.getJobReport(), cloneResult.getErrorReport());
    assertNull(cloneResult.getErrorReport());
    assertNotNull(cloneResult.getWorkspace());
    assertThat(cloneResult.getWorkspace().getResources(), hasSize(10));
    assertEquals(getWorkspaceId(), cloneResult.getWorkspace().getSourceWorkspaceId());
    destinationWorkspaceId = cloneResult.getWorkspace().getDestinationWorkspaceId();
    assertNotNull(destinationWorkspaceId);

    // Verify shared GCS bucket succeeds and is populated
    final ResourceCloneDetails sharedBucketCloneDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(r -> sharedSourceBucket.getResourceId().equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(sharedBucketCloneDetails.toString());
    assertEquals(CloneResourceResult.SUCCEEDED, sharedBucketCloneDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.RESOURCE, sharedBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, sharedBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, sharedBucketCloneDetails.getStewardshipType());
    assertNotNull(sharedBucketCloneDetails.getDestinationResourceId());
    assertNull(sharedBucketCloneDetails.getErrorMessage());
    assertEquals(
        sharedSourceBucket.getGcpBucket().getMetadata().getName(),
        sharedBucketCloneDetails.getName());
    assertEquals(
        sharedSourceBucket.getGcpBucket().getMetadata().getDescription(),
        sharedBucketCloneDetails.getDescription());

    // We need to get the destination bucket name and project ID
    final WorkspaceDescription destinationWorkspace =
        cloningUserWorkspaceApi.getWorkspace(destinationWorkspaceId);
    final String destinationProjectId = destinationWorkspace.getGcpContext().getProjectId();
    final var clonedSharedBucket =
        cloningUserResourceApi.getBucket(
            destinationWorkspaceId, sharedBucketCloneDetails.getDestinationResourceId());
    ResourceModifier.retrieveBucketFile(
        clonedSharedBucket.getAttributes().getBucketName(), destinationProjectId, cloningUser);

    // Verify clone of private bucket fails
    final ResourceCloneDetails privateBucketCloneDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(r -> privateSourceBucket.getResourceId().equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(privateBucketCloneDetails.toString());
    assertEquals(CloneResourceResult.FAILED, privateBucketCloneDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.RESOURCE, privateBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, privateBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, privateBucketCloneDetails.getStewardshipType());
    assertNull(privateBucketCloneDetails.getDestinationResourceId());
    assertNotNull(privateBucketCloneDetails.getErrorMessage());
    assertEquals(
        privateSourceBucket.getGcpBucket().getMetadata().getName(),
        privateBucketCloneDetails.getName());
    assertEquals(
        privateSourceBucket.getGcpBucket().getMetadata().getDescription(),
        privateBucketCloneDetails.getDescription());

    // Verify COPY_NOTHING bucket was skipped
    final ResourceCloneDetails copyNothingBucketCloneDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sharedCopyNothingSourceBucket
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(copyNothingBucketCloneDetails.toString());
    assertEquals(CloneResourceResult.SKIPPED, copyNothingBucketCloneDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.NOTHING, copyNothingBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, copyNothingBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, copyNothingBucketCloneDetails.getStewardshipType());
    assertNull(copyNothingBucketCloneDetails.getDestinationResourceId());
    assertNull(copyNothingBucketCloneDetails.getErrorMessage());
    assertEquals(
        sharedCopyNothingSourceBucket.getGcpBucket().getMetadata().getName(),
        copyNothingBucketCloneDetails.getName());
    assertEquals(
        sharedCopyNothingSourceBucket.getGcpBucket().getMetadata().getDescription(),
        copyNothingBucketCloneDetails.getDescription());

    // verify COPY_DEFINITION bucket exists but is empty
    final ResourceCloneDetails copyDefinitionBucketDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r -> copyDefinitionSourceBucket.getResourceId().equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(copyDefinitionBucketDetails.toString());
    assertEquals(CloneResourceResult.SUCCEEDED, copyDefinitionBucketDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.DEFINITION, copyDefinitionBucketDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, copyDefinitionBucketDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, copyDefinitionBucketDetails.getStewardshipType());
    assertNotNull(copyDefinitionBucketDetails.getDestinationResourceId());
    final GcpGcsBucketResource clonedCopyDefinitionBucket =
        cloningUserResourceApi.getBucket(
            destinationWorkspaceId, copyDefinitionBucketDetails.getDestinationResourceId());
    assertEmptyBucket(
        clonedCopyDefinitionBucket.getAttributes().getBucketName(), destinationProjectId);
    assertEquals(
        copyDefinitionSourceBucket.getGcpBucket().getMetadata().getName(),
        copyDefinitionBucketDetails.getName());
    assertEquals(
        copyDefinitionSourceBucket.getGcpBucket().getMetadata().getDescription(),
        copyDefinitionBucketDetails.getDescription());

    // verify COPY_DEFINITION dataset exists but has no tables
    final ResourceCloneDetails copyDefinitionDatasetDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        copyDefinitionDataset
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(copyDefinitionDatasetDetails.toString());
    assertEquals(CloneResourceResult.SUCCEEDED, copyDefinitionDatasetDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.DEFINITION, copyDefinitionDatasetDetails.getCloningInstructions());
    assertEquals(ResourceType.BIG_QUERY_DATASET, copyDefinitionDatasetDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, copyDefinitionDatasetDetails.getStewardshipType());
    assertNotNull(copyDefinitionDatasetDetails.getDestinationResourceId());
    assertNull(copyDefinitionDatasetDetails.getErrorMessage());
    assertEquals(
        copyDefinitionDataset.getMetadata().getName(), copyDefinitionDatasetDetails.getName());
    assertEquals(
        copyDefinitionDataset.getMetadata().getDescription(),
        copyDefinitionDatasetDetails.getDescription());

    final BigQuery bigQueryClient =
        ClientTestUtils.getGcpBigQueryClient(cloningUser, destinationProjectId);
    assertDatasetHasNoTables(destinationProjectId, bigQueryClient, copyDefinitionDatasetName);

    // verify clone resource dataset succeeded and has rows and tables
    final ResourceCloneDetails copyResourceDatasetDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        copyResourceDataset
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(copyResourceDatasetDetails.toString());
    assertEquals(CloneResourceResult.SUCCEEDED, copyResourceDatasetDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.RESOURCE, copyResourceDatasetDetails.getCloningInstructions());
    assertEquals(ResourceType.BIG_QUERY_DATASET, copyResourceDatasetDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, copyResourceDatasetDetails.getStewardshipType());
    assertNotNull(copyResourceDatasetDetails.getDestinationResourceId());
    assertNull(copyResourceDatasetDetails.getErrorMessage());
    final QueryJobConfiguration employeeQueryJobConfiguration =
        QueryJobConfiguration.newBuilder(
                "SELECT * FROM `"
                    + destinationProjectId
                    + "."
                    + copyResourceDatasetName
                    + ".employee`;")
            .build();
    final TableResult employeeTableResult = bigQueryClient.query(employeeQueryJobConfiguration);
    final long numEmployees =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(numEmployees, is(greaterThanOrEqualTo(2L)));

    // verify private dataset clone failed
    final ResourceCloneDetails privateDatasetDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        privateDataset
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(privateDatasetDetails.toString());
    assertEquals(CloneResourceResult.FAILED, privateDatasetDetails.getResult());
    assertEquals(CloningInstructionsEnum.RESOURCE, privateDatasetDetails.getCloningInstructions());
    assertEquals(ResourceType.BIG_QUERY_DATASET, privateDatasetDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, privateDatasetDetails.getStewardshipType());
    assertNull(privateDatasetDetails.getDestinationResourceId());
    assertNotNull(privateDatasetDetails.getErrorMessage());

    final ResourceCloneDetails bucketReferenceDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceBucketReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    logger.info(bucketReferenceDetails.toString());
    assertEquals(CloneResourceResult.SUCCEEDED, bucketReferenceDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.REFERENCE, bucketReferenceDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, bucketReferenceDetails.getResourceType());
    assertEquals(StewardshipType.REFERENCED, bucketReferenceDetails.getStewardshipType());
    assertNotNull(bucketReferenceDetails.getDestinationResourceId());
    assertNull(bucketReferenceDetails.getErrorMessage());

    final ResourceCloneDetails datasetReferenceDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceDatasetReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    assertEquals(CloneResourceResult.SKIPPED, datasetReferenceDetails.getResult());
    assertEquals(CloningInstructionsEnum.NOTHING, datasetReferenceDetails.getCloningInstructions());
    assertEquals(ResourceType.BIG_QUERY_DATASET, datasetReferenceDetails.getResourceType());
    assertEquals(StewardshipType.REFERENCED, datasetReferenceDetails.getStewardshipType());
    assertNull(datasetReferenceDetails.getDestinationResourceId());
    assertNull(datasetReferenceDetails.getErrorMessage());

    final ResourceCloneDetails dataTableReferenceDetails =
        getOrFail(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceDataTableReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst());
    assertEquals(CloneResourceResult.SKIPPED, dataTableReferenceDetails.getResult());
    assertEquals(
        CloningInstructionsEnum.NOTHING, dataTableReferenceDetails.getCloningInstructions());
    assertEquals(ResourceType.BIG_QUERY_DATA_TABLE, dataTableReferenceDetails.getResourceType());
    assertEquals(StewardshipType.REFERENCED, dataTableReferenceDetails.getStewardshipType());
    assertNull(dataTableReferenceDetails.getDestinationResourceId());
    assertNull(dataTableReferenceDetails.getErrorMessage());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    // Delete the cloned workspace (will delete context and resources)
    if (null != destinationWorkspaceId) {
      cloningUserWorkspaceApi.deleteWorkspace(destinationWorkspaceId);
    }
  }

  private static void assertDatasetHasNoTables(
      String destinationProjectId, BigQuery bigQueryClient, String datasetName)
      throws InterruptedException {
    final QueryJobConfiguration listTablesQuery =
        QueryJobConfiguration.newBuilder(
                "SELECT * FROM `"
                    + destinationProjectId
                    + "."
                    + datasetName
                    + ".INFORMATION_SCHEMA.TABLES`;")
            .build();
    // Will throw not found if the dataset doesn't exist
    final TableResult listTablesResult = bigQueryClient.query(listTablesQuery);
    final long numRows =
        StreamSupport.stream(listTablesResult.getValues().spliterator(), false).count();
    assertEquals(0, numRows);
  }

  private void assertEmptyBucket(String bucketName, String destinationProjectId)
      throws IOException {
    Storage cloningUserStorageClient =
        ClientTestUtils.getGcpStorageClient(cloningUser, destinationProjectId);
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);

    assertNull(cloningUserStorageClient.get(blobId));
  }
}
