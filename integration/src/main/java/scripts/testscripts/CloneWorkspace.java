package scripts.testscripts;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static scripts.utils.BqDatasetUtils.BQ_RESULT_TABLE_NAME;
import static scripts.utils.BqDatasetUtils.makeControlledBigQueryDatasetUserPrivate;
import static scripts.utils.BqDatasetUtils.makeControlledBigQueryDatasetUserShared;
import static scripts.utils.ClientTestUtils.assertPresent;
import static scripts.utils.GcsBucketObjectUtils.makeGcsObjectReference;
import static scripts.utils.GcsBucketUtils.BUCKET_RESOURCE_PREFIX;
import static scripts.utils.GcsBucketUtils.GCS_BLOB_NAME;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserPrivate;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.FolderApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.CloneResourceResult;
import bio.terra.workspace.model.CloneWorkspaceRequest;
import bio.terra.workspace.model.CloneWorkspaceResult;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateFolderRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.Folder;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.ResourceCloneDetails;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.WorkspaceDescription;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.JobInfo.WriteDisposition;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketObjectUtils;
import scripts.utils.GcsBucketUtils;
import scripts.utils.WorkspaceAllocateWithPolicyTestScriptBase;

public class CloneWorkspace extends WorkspaceAllocateWithPolicyTestScriptBase {
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
  private GcpGcsObjectResource sourceBucketFileReference;
  private String copyDefinitionDatasetResourceName;
  private String copyResourceDatasetResourceName;
  private String nameSuffix;
  private String privateDatasetResourceName;
  private String sharedBucketSourceResourceName;
  private String sourceProjectId;
  private TestUserSpecification cloningUser;
  private UUID destinationWorkspaceId;
  private WorkspaceApi cloningUserWorkspaceApi;

  private static final int EXPECTED_NUM_CLONED_RESOURCES = 11;
  private static final String TERRA_FOLDER_ID = "terra-folder-id";
  private static final String FOLDER_DISPLAY_NAME = "folderDisplayName";

  @Override
  protected void doSetup(
      List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    logger.info("Begin setup");
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    // Set up 2 users
    assertThat(testUsers, hasSize(2));
    // User creating the source resources
    final TestUserSpecification sourceOwnerUser = testUsers.get(0);
    // User cloning the workspace
    cloningUser = testUsers.get(1);
    logger.info(
        "Owning user: {}, Cloning user: {}", sourceOwnerUser.userEmail, cloningUser.userEmail);
    // Build source GCP project in main test workspace
    sourceProjectId =
        CloudContextMaker.createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    // Add cloning user as reader on the workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    logger.info(
        "Granted role {} for user {} on workspace {}",
        IamRole.READER,
        cloningUser.userEmail,
        getWorkspaceId());

    // Give users resource APIs
    final ControlledGcpResourceApi sourceOwnerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);
    logger.info("Built API clients for users.");

    // Create a GCS bucket with data
    // create source bucket with COPY_RESOURCE - should clone fine
    nameSuffix = UUID.randomUUID().toString();
    sharedBucketSourceResourceName = BUCKET_RESOURCE_PREFIX + nameSuffix;
    sharedSourceBucket =
        makeControlledGcsBucketUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            sharedBucketSourceResourceName,
            CloningInstructionsEnum.RESOURCE);

    GcsBucketUtils.addFileToBucket(sharedSourceBucket, sourceOwnerUser, sourceProjectId);

    // Create a folder in the workspace, and add controlled GCS bucket resource to it
    addResourceToFolder(sourceOwnerUser, getWorkspaceId(), sharedSourceBucket.getResourceId());

    // Create a private GCS bucket, which the non-creating user can't clone
    privateSourceBucket =
        makeControlledGcsBucketUserPrivate(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            UUID.randomUUID().toString(),
            CloningInstructionsEnum.RESOURCE);
    GcsBucketUtils.addFileToBucket(privateSourceBucket, sourceOwnerUser, sourceProjectId);

    // Create a GCS bucket with data and COPY_NOTHING instruction
    sharedCopyNothingSourceBucket =
        makeControlledGcsBucketUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            UUID.randomUUID().toString(),
            CloningInstructionsEnum.NOTHING);
    GcsBucketUtils.addFileToBucket(sharedCopyNothingSourceBucket, sourceOwnerUser, sourceProjectId);

    // Create a GCS bucket with data and COPY_DEFINITION
    copyDefinitionSourceBucket =
        makeControlledGcsBucketUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            UUID.randomUUID().toString(),
            CloningInstructionsEnum.DEFINITION);
    GcsBucketUtils.addFileToBucket(copyDefinitionSourceBucket, sourceOwnerUser, sourceProjectId);

    // Create a BigQuery Dataset with tables and COPY_DEFINITION
    copyDefinitionDatasetResourceName = "copy_definition_" + nameSuffix.replace('-', '_');
    copyDefinitionDataset =
        makeControlledBigQueryDatasetUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            copyDefinitionDatasetResourceName,
            null,
            CloningInstructionsEnum.DEFINITION);
    BqDatasetUtils.populateBigQueryDataset(copyDefinitionDataset, sourceOwnerUser, sourceProjectId);

    // Create a BigQuery dataset with tables and COPY_RESOURCE
    copyResourceDatasetResourceName = "copy_resource_dataset";
    copyResourceDataset =
        makeControlledBigQueryDatasetUserShared(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            copyResourceDatasetResourceName,
            null,
            CloningInstructionsEnum.RESOURCE);
    BqDatasetUtils.populateBigQueryDataset(copyResourceDataset, sourceOwnerUser, sourceProjectId);

    // Create a private BQ dataset
    privateDatasetResourceName = "private_dataset";
    privateDataset =
        makeControlledBigQueryDatasetUserPrivate(
            sourceOwnerResourceApi,
            getWorkspaceId(),
            privateDatasetResourceName,
            null,
            CloningInstructionsEnum.RESOURCE);

    // Create reference to the shared GCS bucket in this workspace with COPY_REFERENCE
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(sourceOwnerUser, server);
    final String bucketReferenceName = RandomStringUtils.random(16, true, false);

    sourceBucketReference =
        GcsBucketUtils.makeGcsBucketReference(
            sharedSourceBucket.getGcpBucket().getAttributes(),
            referencedGcpResourceApi,
            getWorkspaceId(),
            bucketReferenceName,
            CloningInstructionsEnum.REFERENCE);

    GcpGcsObjectAttributes referencedFileAttributes =
        new GcpGcsObjectAttributes()
            .bucketName(sharedSourceBucket.getGcpBucket().getAttributes().getBucketName())
            .fileName(GCS_BLOB_NAME);
    sourceBucketFileReference =
        makeGcsObjectReference(
            referencedFileAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            "a_reference_to_wsmtestblob",
            CloningInstructionsEnum.REFERENCE);

    // Create a folder in the workspace, and add reference GCS bucket resource to it
    addResourceToFolder(
        sourceOwnerUser, getWorkspaceId(), sourceBucketFileReference.getMetadata().getResourceId());

    // Create reference to the shared BQ dataset with COPY_DEFINITION
    sourceDatasetReference =
        BqDatasetUtils.makeBigQueryDatasetReference(
            copyDefinitionDataset.getAttributes(),
            referencedGcpResourceApi,
            getWorkspaceId(),
            "dataset_resource_1");
    GcpBigQueryDataTableAttributes bqTableReferenceAttributes =
        new GcpBigQueryDataTableAttributes()
            .projectId(copyDefinitionDataset.getAttributes().getProjectId())
            .datasetId(copyDefinitionDataset.getAttributes().getDatasetId())
            .dataTableId(BqDatasetUtils.BQ_EMPLOYEE_TABLE_NAME);
    sourceDataTableReference =
        BqDatasetUtils.makeBigQueryDataTableReference(
            bqTableReferenceAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            "datatable_resource_1");
    logger.info("End setup");
  }

  @Override
  protected void doUserJourney(
      TestUserSpecification sourceOwnerUser, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    // Verily deployment doesn't have janitor. For nightly tests, need different userFacingId for
    // each environment.
    String destinationUserFacingId = "cloned-workspace-" + UUID.randomUUID();
    logger.info("Start User Journey");
    // As reader user, clone the workspace
    // Get a new workspace API for the reader
    cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);

    final CloneWorkspaceRequest cloneWorkspaceRequest =
        new CloneWorkspaceRequest()
            .userFacingId(destinationUserFacingId)
            .description("A clone of workspace " + getWorkspaceId().toString())
            .spendProfile(getSpendProfileId()) // TODO- use a different one if available
            .location("us-central1");
    CloneWorkspaceResult cloneResult =
        cloningUserWorkspaceApi.cloneWorkspace(cloneWorkspaceRequest, getWorkspaceId());
    logger.info("Started clone of workspace {}", getWorkspaceId());

    final String jobId = cloneResult.getJobReport().getId();
    logger.info("Clone Job ID {}", jobId);
    final UUID destinationWorkspaceId = cloneResult.getWorkspace().getDestinationWorkspaceId();
    assertNotNull(destinationWorkspaceId, "Destination workspace ID available immediately.");
    assertEquals(destinationUserFacingId, cloneResult.getWorkspace().getDestinationUserFacingId());
    final WorkspaceDescription destinationWorkspaceDescription =
        cloningUserWorkspaceApi.getWorkspace(destinationWorkspaceId, /*minimumHighestRole=*/ null);
    assertNotNull(
        destinationWorkspaceDescription,
        "Destination workspace is available in DB immediately after return from cloneWorkspace().");
    assertEquals(
        destinationWorkspaceId, destinationWorkspaceDescription.getId(), "Destination IDs match");
    assertEquals(
        sourceOwnerWorkspaceApi
                .getWorkspace(getWorkspaceId(), /*minimumHighestRole=*/ null)
                .getUserFacingId()
            + " (Copy)",
        destinationWorkspaceDescription.getDisplayName(),
        "Destination displayName matches");

    cloneResult =
        ClientTestUtils.pollWhileRunning(
            cloneResult,
            // TODO(PF-1825): unlike the individual clone resource endpoints, the clone workspace
            //  endpoint associates the cloning job with the destination workspace ID.
            () -> cloningUserWorkspaceApi.getCloneWorkspaceResult(destinationWorkspaceId, jobId),
            CloneWorkspaceResult::getJobReport,
            Duration.ofSeconds(10));
    logger.info("Completed clone result: {}", cloneResult);

    ClientTestUtils.assertJobSuccess(
        "Clone Workspace", cloneResult.getJobReport(), cloneResult.getErrorReport());
    assertNull(cloneResult.getErrorReport(), "Error report should be null for successful clone.");
    assertNotNull(cloneResult.getWorkspace(), "Workspace should be populated.");
    assertThat(
        "Cloned workspace has expected number of resources.",
        cloneResult.getWorkspace().getResources(),
        hasSize(EXPECTED_NUM_CLONED_RESOURCES));
    assertEquals(
        getWorkspaceId(),
        cloneResult.getWorkspace().getSourceWorkspaceId(),
        "Source workspace ID reported accurately.");
    assertEquals(
        destinationWorkspaceDescription.getProperties(),
        sourceOwnerWorkspaceApi
            .getWorkspace(getWorkspaceId(), /*minimumHighestRole=*/ null)
            .getProperties(),
        "Properties cloned successfully");

    // Verify shared GCS bucket succeeds and is populated
    final ResourceCloneDetails sharedBucketCloneDetails =
        assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(r -> sharedSourceBucket.getResourceId().equals(r.getSourceResourceId()))
                .findFirst(),
            "Shared bucket included in workspace clone results.");
    logger.info("Expected success: {}", sharedBucketCloneDetails);
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        sharedBucketCloneDetails.getResult(),
        "Shared Bucket clone succeeded.");
    assertEquals(
        CloningInstructionsEnum.RESOURCE,
        sharedBucketCloneDetails.getCloningInstructions(),
        "RESOURCE cloning instructions preserved.");
    assertEquals(
        ResourceType.GCS_BUCKET,
        sharedBucketCloneDetails.getResourceType(),
        "Resource Type preserved.");
    assertEquals(
        StewardshipType.CONTROLLED,
        sharedBucketCloneDetails.getStewardshipType(),
        "Stewardship Type preserved.");
    assertNotNull(
        sharedBucketCloneDetails.getDestinationResourceId(), "Destination Resource ID populated.");
    assertNull(
        sharedBucketCloneDetails.getErrorMessage(),
        "No error message present for successful resource clone.");
    assertEquals(
        sharedSourceBucket.getGcpBucket().getMetadata().getName(),
        sharedBucketCloneDetails.getName(),
        "Resource name is preserved.");
    assertEquals(
        sharedSourceBucket.getGcpBucket().getMetadata().getDescription(),
        sharedBucketCloneDetails.getDescription(),
        "Description is preserved.");

    // We need to get the destination bucket name and project ID
    final WorkspaceDescription destinationWorkspace =
        cloningUserWorkspaceApi.getWorkspace(destinationWorkspaceId, /*minimumHighestRole=*/ null);
    assertEquals(destinationUserFacingId, destinationWorkspace.getUserFacingId());
    final String destinationProjectId = destinationWorkspace.getGcpContext().getProjectId();
    final var clonedSharedBucket =
        cloningUserResourceApi.getBucket(
            destinationWorkspaceId, sharedBucketCloneDetails.getDestinationResourceId());
    logger.info("Cloned Shared Bucket: {}", clonedSharedBucket);
    GcsBucketObjectUtils.retrieveBucketFile(
        clonedSharedBucket.getAttributes().getBucketName(), destinationProjectId, cloningUser);

    // Assert the destination workspace preserves the cloned folder with the cloned controlled GCS
    // bucket resource
    assertFolderInWorkspace(
        cloneResult.getWorkspace().getDestinationWorkspaceId(),
        UUID.fromString(
            clonedSharedBucket.getMetadata().getProperties().stream()
                .filter(x -> x.getKey().equals(TERRA_FOLDER_ID))
                .collect(onlyElement())
                .getValue()));

    // Verify clone of private bucket fails
    final ResourceCloneDetails privateBucketCloneDetails =
        assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(r -> privateSourceBucket.getResourceId().equals(r.getSourceResourceId()))
                .findFirst(),
            "Private bucket included in workspace clone details.");
    logger.info("Private Bucket (expected failure): {}", privateBucketCloneDetails);
    assertEquals(
        CloneResourceResult.FAILED, privateBucketCloneDetails.getResult(), "Reports failure.");
    assertEquals(
        CloningInstructionsEnum.RESOURCE,
        privateBucketCloneDetails.getCloningInstructions(),
        "preserves cloning instructions.");
    assertEquals(
        ResourceType.GCS_BUCKET,
        privateBucketCloneDetails.getResourceType(),
        "preserves Resource Type.");
    assertEquals(
        StewardshipType.CONTROLLED,
        privateBucketCloneDetails.getStewardshipType(),
        "preserves Stewardship Type");
    assertNull(
        privateBucketCloneDetails.getDestinationResourceId(),
        "Destination Resource ID is not populated for failed clone.");
    assertNotNull(privateBucketCloneDetails.getErrorMessage(), "Error message is present.");
    assertEquals(
        privateSourceBucket.getGcpBucket().getMetadata().getName(),
        privateBucketCloneDetails.getName(),
        "Bucket resource name is preserved.");
    assertEquals(
        privateSourceBucket.getGcpBucket().getMetadata().getDescription(),
        privateBucketCloneDetails.getDescription(),
        "Description is preserved.");

    // Verify COPY_NOTHING bucket was skipped
    final ResourceCloneDetails copyNothingBucketCloneDetails =
        assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sharedCopyNothingSourceBucket
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Shared copy nothing bucket included in workspace clone results.");
    logger.info("Copy Nothing Bucket (expected SKIPPED): {}", copyNothingBucketCloneDetails);
    assertEquals(
        CloneResourceResult.SKIPPED,
        copyNothingBucketCloneDetails.getResult(),
        "Result is SKIPPED");
    assertEquals(
        CloningInstructionsEnum.NOTHING,
        copyNothingBucketCloneDetails.getCloningInstructions(),
        "Cloning instructions of Nothing honored and preserved.");
    assertEquals(
        ResourceType.GCS_BUCKET,
        copyNothingBucketCloneDetails.getResourceType(),
        "Resource type preserved");
    assertEquals(
        StewardshipType.CONTROLLED,
        copyNothingBucketCloneDetails.getStewardshipType(),
        "Stewardship Type preserved");
    assertNull(
        copyNothingBucketCloneDetails.getDestinationResourceId(),
        "Destination resource ID omitted for skipped resource.");
    assertNull(
        copyNothingBucketCloneDetails.getErrorMessage(),
        "No error message for successfully skipped resource.");
    assertEquals(
        sharedCopyNothingSourceBucket.getGcpBucket().getMetadata().getName(),
        copyNothingBucketCloneDetails.getName(),
        "Resource name is preserved");
    assertEquals(
        sharedCopyNothingSourceBucket.getGcpBucket().getMetadata().getDescription(),
        copyNothingBucketCloneDetails.getDescription(),
        "Description is preserved.");

    // verify COPY_DEFINITION bucket exists but is empty
    final ResourceCloneDetails copyDefinitionBucketDetails =
        assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r -> copyDefinitionSourceBucket.getResourceId().equals(r.getSourceResourceId()))
                .findFirst(),
            "Copy definition bucket included in workspace clone results.");
    logger.info("Copy Definition bucket (expected success) {}", copyDefinitionBucketDetails);
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        copyDefinitionBucketDetails.getResult(),
        "Copy of COPY_DEFINITION bucket succeeded.");
    assertEquals(
        CloningInstructionsEnum.DEFINITION,
        copyDefinitionBucketDetails.getCloningInstructions(),
        "Cloning instructions preserved.");
    assertEquals(
        ResourceType.GCS_BUCKET,
        copyDefinitionBucketDetails.getResourceType(),
        "Resource Type preserved.");
    assertEquals(
        StewardshipType.CONTROLLED,
        copyDefinitionBucketDetails.getStewardshipType(),
        "Stewardship Type Preserved.");
    assertNotNull(
        copyDefinitionBucketDetails.getDestinationResourceId(),
        "Destination resource ID populated.");
    final GcpGcsBucketResource clonedCopyDefinitionBucket =
        cloningUserResourceApi.getBucket(
            destinationWorkspaceId, copyDefinitionBucketDetails.getDestinationResourceId());
    assertEmptyBucket(
        clonedCopyDefinitionBucket.getAttributes().getBucketName(), destinationProjectId);
    assertEquals(
        copyDefinitionSourceBucket.getGcpBucket().getMetadata().getName(),
        copyDefinitionBucketDetails.getName(),
        "Copy definition bucket name is preserved.");
    assertEquals(
        copyDefinitionSourceBucket.getGcpBucket().getMetadata().getDescription(),
        copyDefinitionBucketDetails.getDescription(),
        "Copy definition bucket description is preserved.");

    // verify COPY_DEFINITION dataset exists but has no tables
    final ResourceCloneDetails copyDefinitionDatasetDetails =
        assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        copyDefinitionDataset
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Copy Definition BQ Dataset is included in workspace clone details.");
    logger.info("Copy Definition Dataset (expected success): {}", copyDefinitionDatasetDetails);
    assertEquals(
        CloneResourceResult.SUCCEEDED, copyDefinitionDatasetDetails.getResult(), "Clone Succeeded");
    assertEquals(
        CloningInstructionsEnum.DEFINITION,
        copyDefinitionDatasetDetails.getCloningInstructions(),
        "Cloning instructions preserved.");
    assertEquals(
        ResourceType.BIG_QUERY_DATASET,
        copyDefinitionDatasetDetails.getResourceType(),
        "Resource Type preserved");
    assertEquals(
        StewardshipType.CONTROLLED,
        copyDefinitionDatasetDetails.getStewardshipType(),
        "Stewardship Type preserved");
    assertNotNull(
        copyDefinitionDatasetDetails.getDestinationResourceId(),
        "Destination resource ID populated.");
    assertNull(
        copyDefinitionDatasetDetails.getErrorMessage(),
        "Error message omitted for successful clone");
    assertEquals(
        copyDefinitionDataset.getMetadata().getName(),
        copyDefinitionDatasetDetails.getName(),
        "Resource name preserved.");
    assertEquals(
        copyDefinitionDataset.getMetadata().getDescription(),
        copyDefinitionDatasetDetails.getDescription(),
        "Description preserved.");

    final BigQuery bigQueryClient =
        ClientTestUtils.getGcpBigQueryClient(cloningUser, destinationProjectId);
    assertDatasetHasNoTables(
        destinationProjectId, bigQueryClient, copyDefinitionDatasetResourceName);

    // verify clone resource dataset succeeded and has rows and tables
    final ResourceCloneDetails copyResourceDatasetDetails =
        assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        copyResourceDataset
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "COPY_RESOURCE dataset is included in workspace clone details.");
    logger.info("COPY_RESOURCE dataset (expected success): {}", copyResourceDatasetDetails);
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        copyResourceDatasetDetails.getResult(),
        "COPY_RESOURCE dataset successfully cloned");
    assertEquals(
        CloningInstructionsEnum.RESOURCE,
        copyResourceDatasetDetails.getCloningInstructions(),
        "Cloning instructions preserved.");
    assertEquals(
        ResourceType.BIG_QUERY_DATASET,
        copyResourceDatasetDetails.getResourceType(),
        "Resource Type preserved");
    assertEquals(
        StewardshipType.CONTROLLED,
        copyResourceDatasetDetails.getStewardshipType(),
        "Stewardship Type preserved.");
    assertNotNull(
        copyResourceDatasetDetails.getDestinationResourceId(),
        "Destination Resourcde ID populated.");
    assertNull(copyResourceDatasetDetails.getErrorMessage(), "Error message omitted for success");

    // Use cloned result table to avoid Domain Restricted Sharing conflicts created by temporary
    // result table IAM.
    TableId resultTableId =
        TableId.of(destinationProjectId, copyResourceDatasetResourceName, BQ_RESULT_TABLE_NAME);
    final QueryJobConfiguration employeeQueryJobConfiguration =
        QueryJobConfiguration.newBuilder(
                "SELECT * FROM `"
                    + destinationProjectId
                    + "."
                    + copyResourceDatasetResourceName
                    + ".employee`;")
            .setDestinationTable(resultTableId)
            .setWriteDisposition(WriteDisposition.WRITE_TRUNCATE)
            .build();
    final TableResult employeeTableResult = bigQueryClient.query(employeeQueryJobConfiguration);
    logger.info("Queried COPY_RESOURCE dataset for employee count");
    final long numEmployees =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(
        "Correct number of employees inserted (with possible duplicates)",
        numEmployees,
        is(greaterThanOrEqualTo(2L)));

    // verify private dataset clone failed
    final ResourceCloneDetails privateDatasetDetails =
        ClientTestUtils.assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        privateDataset
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Private dataset clone results located.");
    logger.info("Private dataset (expected failure): {}", privateDatasetDetails);
    assertEquals(
        CloneResourceResult.FAILED,
        privateDatasetDetails.getResult(),
        "Attempt to clone private dataset failed");
    assertEquals(
        CloningInstructionsEnum.RESOURCE,
        privateDatasetDetails.getCloningInstructions(),
        "Cloning instructions preserved.");
    assertEquals(
        ResourceType.BIG_QUERY_DATASET,
        privateDatasetDetails.getResourceType(),
        "Resource Type preserved");
    assertEquals(
        StewardshipType.CONTROLLED,
        privateDatasetDetails.getStewardshipType(),
        "Stewardship Type preserved");
    assertNull(
        privateDatasetDetails.getDestinationResourceId(),
        "Destination resource ID omitted for failed resource clone");
    assertNotNull(
        privateDatasetDetails.getErrorMessage(),
        "Error message populated for failed resource clone.");

    final ResourceCloneDetails bucketReferenceDetails =
        ClientTestUtils.assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceBucketReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Bucket reference clone results included.");
    logger.info("Bucket reference (expected success): {}", bucketReferenceDetails);
    assertEquals(
        CloneResourceResult.SUCCEEDED, bucketReferenceDetails.getResult(), "Success reported");
    assertEquals(
        CloningInstructionsEnum.REFERENCE,
        bucketReferenceDetails.getCloningInstructions(),
        "Cloning instructinos preserved");
    assertEquals(
        ResourceType.GCS_BUCKET,
        bucketReferenceDetails.getResourceType(),
        "resource type preserved");
    assertEquals(
        StewardshipType.REFERENCED,
        bucketReferenceDetails.getStewardshipType(),
        "stewardship type preserved");
    assertEquals(
        sourceBucketReference.getMetadata().getName(),
        bucketReferenceDetails.getName(),
        "Resource name preserved");
    assertEquals(
        sourceBucketReference.getMetadata().getDescription(),
        bucketReferenceDetails.getDescription(),
        "Description preserved");
    assertNotNull(
        bucketReferenceDetails.getDestinationResourceId(), "destination resource ID populated");
    assertNull(bucketReferenceDetails.getErrorMessage(), "Error message omitted");

    final ResourceCloneDetails bucketFileReferenceDetails =
        ClientTestUtils.assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceBucketFileReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Bucket file reference clone details present");
    logger.info("Reference to GCS Bucket File (expected success): {}", bucketFileReferenceDetails);

    ReferencedGcpResourceApi cloningUserReferencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(cloningUser, server);

    GcpGcsObjectResource clonedBucketFileReference =
        cloningUserReferencedGcpResourceApi.getGcsObjectReference(
            cloneResult.getWorkspace().getDestinationWorkspaceId(),
            bucketFileReferenceDetails.getDestinationResourceId());

    // Assert the destination workspace preserves the cloned folder with the cloned reference GCS
    // bucket resource
    assertFolderInWorkspace(
        cloneResult.getWorkspace().getDestinationWorkspaceId(),
        UUID.fromString(
            clonedBucketFileReference.getMetadata().getProperties().stream()
                .filter(x -> x.getKey().equals(TERRA_FOLDER_ID))
                .collect(onlyElement())
                .getValue()));

    assertEquals(
        CloneResourceResult.SUCCEEDED,
        bucketFileReferenceDetails.getResult(),
        "bucket file reference clone succeeded");
    assertEquals(
        CloningInstructionsEnum.REFERENCE,
        bucketFileReferenceDetails.getCloningInstructions(),
        "Cloning instructions preserved.");
    assertEquals(
        ResourceType.GCS_OBJECT,
        bucketFileReferenceDetails.getResourceType(),
        "Resource type preserved");
    assertEquals(
        StewardshipType.REFERENCED,
        bucketFileReferenceDetails.getStewardshipType(),
        "Stewardship type preserved");
    assertNotNull(
        bucketFileReferenceDetails.getDestinationResourceId(), "Destination resource ID populated");
    assertNull(bucketFileReferenceDetails.getErrorMessage(), "Error message omitted.");

    final ResourceCloneDetails datasetReferenceDetails =
        ClientTestUtils.assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceDatasetReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Dataset reference clone results included");
    logger.info("Dataset reference clone details (expected skipped): {}", datasetReferenceDetails);
    assertEquals(
        CloneResourceResult.SKIPPED, datasetReferenceDetails.getResult(), "Resource was skipped");
    assertEquals(
        CloningInstructionsEnum.NOTHING,
        datasetReferenceDetails.getCloningInstructions(),
        "Clone instructions preserved");
    assertEquals(
        ResourceType.BIG_QUERY_DATASET,
        datasetReferenceDetails.getResourceType(),
        "resource type preserved");
    assertEquals(
        StewardshipType.REFERENCED,
        datasetReferenceDetails.getStewardshipType(),
        "stewardship type preserved");
    assertNull(
        datasetReferenceDetails.getDestinationResourceId(),
        "Destination resource ID omitted for skipped resource clone");
    assertNull(
        datasetReferenceDetails.getErrorMessage(),
        "Error message omitted for skipped resource clone.");

    final ResourceCloneDetails dataTableReferenceDetails =
        ClientTestUtils.assertPresent(
            cloneResult.getWorkspace().getResources().stream()
                .filter(
                    r ->
                        sourceDataTableReference
                            .getMetadata()
                            .getResourceId()
                            .equals(r.getSourceResourceId()))
                .findFirst(),
            "Data table reference clone result included");
    logger.info("Data table reference clone result (expect skipped): {}", datasetReferenceDetails);
    assertEquals(
        CloneResourceResult.SKIPPED,
        dataTableReferenceDetails.getResult(),
        "result of skip is SKIPPED");
    assertEquals(
        CloningInstructionsEnum.NOTHING,
        dataTableReferenceDetails.getCloningInstructions(),
        "cloning instructions preserved");
    assertEquals(
        ResourceType.BIG_QUERY_DATA_TABLE,
        dataTableReferenceDetails.getResourceType(),
        "resource type preserved");
    assertEquals(
        StewardshipType.REFERENCED,
        dataTableReferenceDetails.getStewardshipType(),
        "stewardship type preserved");
    assertNull(
        dataTableReferenceDetails.getDestinationResourceId(),
        "Destination resource ID omitted for skipped resource");
    assertNull(dataTableReferenceDetails.getErrorMessage(), "No error message for successful skip");
    logger.info("End User Journey");
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

  private void addResourceToFolder(
      TestUserSpecification sourceOwnerUser, UUID workspaceId, UUID resourceId) throws Exception {
    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(sourceOwnerUser, server);

    // Create folder in the source workspace
    FolderApi folderApi = new FolderApi(ownerApiClient);
    String displayName = FOLDER_DISPLAY_NAME + "_" + UUID.randomUUID();
    folderApi.createFolder(
        new CreateFolderRequestBody().displayName(displayName), getWorkspaceId());
    Folder folder =
        folderApi.listFolders(getWorkspaceId()).getFolders().stream()
            .filter(f -> f.getDisplayName().equals(displayName))
            .collect(onlyElement());

    // Update resource properties with new folder id
    ResourceApi resourceApi = new ResourceApi(ownerApiClient);
    resourceApi.updateResourceProperties(
        List.of(new Property().key(TERRA_FOLDER_ID).value(folder.getId().toString())),
        workspaceId,
        resourceId);
  }

  private void assertFolderInWorkspace(UUID workspaceId, UUID folderId) throws Exception {
    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(cloningUser, server);
    FolderApi folderApi = new FolderApi(ownerApiClient);

    // Check if there is one folder with the expected folder id.
    // Note: onlyElement() throws an IllegalArgumentException if the stream consists
    // of two or more elements, and a NoSuchElementException if the stream is empty.
    Folder actualFolder =
        folderApi.listFolders(workspaceId).getFolders().stream()
            .filter(x -> x.getId().equals(folderId))
            .collect(onlyElement());
    assertNotNull(actualFolder);
  }

  private static void assertDatasetHasNoTables(
      String destinationProjectId, BigQuery bigQueryClient, String datasetName)
      throws InterruptedException {
    // The result table will not be created if there are no results.
    TableId resultTableId = TableId.of(destinationProjectId, datasetName, "FAKE TABLE NAME");
    final QueryJobConfiguration listTablesQuery =
        QueryJobConfiguration.newBuilder(
                "SELECT * FROM `"
                    + destinationProjectId
                    + "."
                    + datasetName
                    + ".INFORMATION_SCHEMA.TABLES`;")
            .setDestinationTable(resultTableId)
            .setWriteDisposition(WriteDisposition.WRITE_TRUNCATE)
            .build();
    // Will throw not found if the dataset doesn't exist
    final TableResult listTablesResult = bigQueryClient.query(listTablesQuery);
    final long numRows =
        StreamSupport.stream(listTablesResult.getValues().spliterator(), false).count();
    assertEquals(0, numRows, "Expected zero tables for COPY_DEFINITION dataset");
    logger.info(
        "BQ Dataset {} in project {} has no tables, as expected.",
        datasetName,
        destinationProjectId);
  }

  private void assertEmptyBucket(String bucketName, String destinationProjectId)
      throws IOException {
    Storage cloningUserStorageClient =
        ClientTestUtils.getGcpStorageClient(cloningUser, destinationProjectId);
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);

    assertNull(cloningUserStorageClient.get(blobId), "Returned blob should be null");
    logger.info(
        "COPY_DEFINITION Bucket {} does not contain blob {}, as expected.",
        bucketName,
        GCS_BLOB_NAME);
  }
}
