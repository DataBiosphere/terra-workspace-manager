package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.model.CloneReferencedGcpGcsObjectResourceResult;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import scripts.utils.ClientTestUtils;
import scripts.utils.CommonResourceFieldsUtil;
import scripts.utils.GcsBucketObjectUtils;
import scripts.utils.GcsBucketUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.ParameterUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ReferencedGcsResourceLifecycle extends WorkspaceAllocateTestScriptBase {

  private TestUserSpecification noAccessUser;
  private TestUserSpecification partialAccessUser;
  private GcpGcsBucketAttributes gcsUniformAccessBucketAttributes;
  private UUID bucketResourceId;
  private GcpGcsBucketAttributes gcsFineGrainedAccessBucketAttributes;
  private UUID fineGrainedBucketResourceId;
  private GcpGcsObjectAttributes gcsFileAttributes;
  private UUID fileResourceId;
  private GcpGcsObjectAttributes gcsFolderAttributes;
  private UUID folderResourceId;
  private UUID destinationWorkspaceId;

  public void setParametersMap(Map<String, String> parametersMap) throws Exception {
    super.setParametersMap(parametersMap);
    gcsUniformAccessBucketAttributes = ParameterUtils.getUniformBucketReference(parametersMap);
    gcsFineGrainedAccessBucketAttributes =
        ParameterUtils.getFineGrainedBucketReference(parametersMap);
    gcsFileAttributes = ParameterUtils.getGcsFileReference(parametersMap);
    gcsFolderAttributes = ParameterUtils.getGcsFolderReference(parametersMap);
  }

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.noAccessUser = testUsers.get(1);
    this.partialAccessUser = testUsers.get(2);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);
    // Grant secondary users READER permission in the workspace.
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), partialAccessUser, IamRole.READER);
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), noAccessUser, IamRole.READER);

    // Create the references
    GcpGcsBucketResource referencedBucket =
        GcsBucketUtils.makeGcsBucketReference(
            gcsUniformAccessBucketAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            CloningInstructionsEnum.REFERENCE);
    bucketResourceId = referencedBucket.getMetadata().getResourceId();
    assertEquals(
        CommonResourceFieldsUtil.getResourceDefaultProperties(),
        referencedBucket.getMetadata().getProperties());
    GcpGcsBucketResource fineGrainedBucket =
        GcsBucketUtils.makeGcsBucketReference(
            gcsFineGrainedAccessBucketAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            CloningInstructionsEnum.REFERENCE);
    fineGrainedBucketResourceId = fineGrainedBucket.getMetadata().getResourceId();
    GcpGcsObjectResource referencedGcsFile =
        GcsBucketObjectUtils.makeGcsObjectReference(
            gcsFileAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            CloningInstructionsEnum.REFERENCE);
    fileResourceId = referencedGcsFile.getMetadata().getResourceId();
    assertEquals(
        CommonResourceFieldsUtil.getResourceDefaultProperties(),
        referencedGcsFile.getMetadata().getProperties());
    GcpGcsObjectResource referencedGcsFolder =
        GcsBucketObjectUtils.makeGcsObjectReference(
            gcsFolderAttributes,
            referencedGcpResourceApi,
            getWorkspaceId(),
            MultiResourcesUtils.makeName(),
            CloningInstructionsEnum.REFERENCE);
    folderResourceId = referencedGcsFolder.getMetadata().getResourceId();

    // Get the references
    testGetReferences(
        referencedBucket,
        fineGrainedBucket,
        referencedGcsFile,
        referencedGcsFolder,
        referencedGcpResourceApi);

    // Create a second workspace to clone references into, owned by the same user
    testCloneReference(
        referencedBucket,
        fineGrainedBucket,
        referencedGcsFile,
        referencedGcsFolder,
        referencedGcpResourceApi,
        workspaceApi);

    // Validate reference access
    testValidateReference(testUser);

    // Update the references
    testUpdateReferences(fineGrainedBucket, referencedGcpResourceApi);

    // Delete the references
    referencedGcpResourceApi.deleteBucketReference(getWorkspaceId(), bucketResourceId);
    referencedGcpResourceApi.deleteBucketReference(getWorkspaceId(), fineGrainedBucketResourceId);
    referencedGcpResourceApi.deleteGcsObjectReference(getWorkspaceId(), fileResourceId);
    referencedGcpResourceApi.deleteGcsObjectReference(getWorkspaceId(), folderResourceId);

    // Enumerating all resources with no filters should be empty
    ResourceApi resourceApi = ClientTestUtils.getResourceClient(testUser, server);
    ResourceList enumerateResult =
        resourceApi.enumerateResources(getWorkspaceId(), 0, 100, null, null);
    assertTrue(enumerateResult.getResources().isEmpty());
  }

  private void testGetReferences(
      GcpGcsBucketResource uniformBucketReference,
      GcpGcsBucketResource fineGrainedBucketReference,
      GcpGcsObjectResource fileReference,
      GcpGcsObjectResource folderReference,
      ReferencedGcpResourceApi referencedGcpResourceApi)
      throws Exception {
    GcpGcsBucketResource fetchedBucket =
        referencedGcpResourceApi.getBucketReference(getWorkspaceId(), bucketResourceId);
    assertEquals(uniformBucketReference, fetchedBucket);
    GcpGcsBucketResource fetchedFineGrainedBucket =
        referencedGcpResourceApi.getBucketReference(getWorkspaceId(), fineGrainedBucketResourceId);
    assertEquals(fineGrainedBucketReference, fetchedFineGrainedBucket);
    GcpGcsObjectResource fetchedGcsFile =
        referencedGcpResourceApi.getGcsObjectReference(getWorkspaceId(), fileResourceId);
    assertEquals(fileReference, fetchedGcsFile);
    GcpGcsObjectResource fetchedGcsFolder =
        referencedGcpResourceApi.getGcsObjectReference(getWorkspaceId(), folderResourceId);
    assertEquals(folderReference, fetchedGcsFolder);

    // Enumerate the references
    // Any workspace member can view references in WSM, even if they can't view the underlying cloud
    // resource or contents.
    ResourceApi noAccessApi = ClientTestUtils.getResourceClient(noAccessUser, server);
    ResourceList referenceList =
        noAccessApi.enumerateResources(
            getWorkspaceId(), 0, 5, /* referenceType= */ null, StewardshipType.REFERENCED);
    assertEquals(4, referenceList.getResources().size());
    ResourceList bucketList =
        noAccessApi.enumerateResources(
            getWorkspaceId(),
            0,
            5,
            /* referenceType= */ ResourceType.GCS_BUCKET,
            StewardshipType.REFERENCED);
    assertEquals(2, bucketList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.GCS_BUCKET, bucketList);
    ResourceList fileList =
        noAccessApi.enumerateResources(
            getWorkspaceId(),
            0,
            5,
            /* referenceType= */ ResourceType.GCS_OBJECT,
            StewardshipType.REFERENCED);
    assertEquals(2, fileList.getResources().size());
    MultiResourcesUtils.assertResourceType(ResourceType.GCS_OBJECT, fileList);
  }

  private void testCloneReference(
      GcpGcsBucketResource uniformBucketReference,
      GcpGcsBucketResource fineGrainedBucketReference,
      GcpGcsObjectResource fileReference,
      GcpGcsObjectResource folderReference,
      ReferencedGcpResourceApi referencedGcpResourceApi,
      WorkspaceApi workspaceApi)
      throws Exception {
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
    // Clone references
    CloneReferencedGcpGcsBucketResourceResult bucketCloneResult =
        referencedGcpResourceApi.cloneGcpGcsBucketReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            bucketResourceId);
    assertEquals(getWorkspaceId(), bucketCloneResult.getSourceWorkspaceId());
    assertEquals(
        uniformBucketReference.getAttributes(), bucketCloneResult.getResource().getAttributes());
    CloneReferencedGcpGcsBucketResourceResult fineGrainedBucketCloneResult =
        referencedGcpResourceApi.cloneGcpGcsBucketReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            fineGrainedBucketResourceId);
    assertEquals(getWorkspaceId(), fineGrainedBucketCloneResult.getSourceWorkspaceId());
    assertEquals(
        fineGrainedBucketReference.getAttributes(),
        fineGrainedBucketCloneResult.getResource().getAttributes());
    CloneReferencedGcpGcsObjectResourceResult fileCloneResult =
        referencedGcpResourceApi.cloneGcpGcsObjectReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            fileResourceId);
    assertEquals(getWorkspaceId(), fileCloneResult.getSourceWorkspaceId());
    assertEquals(fileReference.getAttributes(), fileCloneResult.getResource().getAttributes());
    CloneReferencedGcpGcsObjectResourceResult folderCloneResult =
        referencedGcpResourceApi.cloneGcpGcsObjectReference(
            new CloneReferencedResourceRequestBody().destinationWorkspaceId(destinationWorkspaceId),
            getWorkspaceId(),
            folderResourceId);
    assertEquals(getWorkspaceId(), folderCloneResult.getSourceWorkspaceId());
    assertEquals(folderReference.getAttributes(), folderCloneResult.getResource().getAttributes());
  }

  private void testValidateReference(TestUserSpecification owner) throws Exception {
    ResourceApi ownerApi = ClientTestUtils.getResourceClient(owner, server);
    ResourceApi noAccessUserApi = ClientTestUtils.getResourceClient(noAccessUser, server);
    ResourceApi fileReaderApi = ClientTestUtils.getResourceClient(partialAccessUser, server);

    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));

    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));
    assertFalse(fileReaderApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));
    assertFalse(
        noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fineGrainedBucketResourceId));

    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), fileResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), fileResourceId));
    assertTrue(fileReaderApi.checkReferenceAccess(getWorkspaceId(), fileResourceId));

    assertTrue(ownerApi.checkReferenceAccess(getWorkspaceId(), folderResourceId));
    assertFalse(noAccessUserApi.checkReferenceAccess(getWorkspaceId(), folderResourceId));
    assertFalse(fileReaderApi.checkReferenceAccess(getWorkspaceId(), folderResourceId));
  }

  private void testUpdateReferences(
      GcpGcsBucketResource fineGrainedBucket, ReferencedGcpResourceApi fullAccessApi)
      throws Exception {
    ReferencedGcpResourceApi partialAccessApi =
        ClientTestUtils.getReferencedGcpResourceClient(partialAccessUser, server);
    ResourceApi partialAccessResourceApi =
        ClientTestUtils.getResourceClient(partialAccessUser, server);
    // Update GCS bucket's name and description
    String newBucketName = "newGcsBucket";
    String newBucketDescription = "a new description to the new bucket reference";
    GcpGcsBucketResource bucketReferenceFirstUpdate =
        GcsBucketUtils.updateGcsBucketReference(
            fullAccessApi,
            getWorkspaceId(),
            bucketResourceId,
            newBucketName,
            newBucketDescription,
            null,
            CloningInstructionsEnum.NOTHING);
    assertEquals(newBucketName, bucketReferenceFirstUpdate.getMetadata().getName());
    assertEquals(newBucketDescription, bucketReferenceFirstUpdate.getMetadata().getDescription());
    assertEquals(
        gcsUniformAccessBucketAttributes.getBucketName(),
        bucketReferenceFirstUpdate.getAttributes().getBucketName());
    assertTrue(partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), bucketResourceId));
    assertEquals(
        CloningInstructionsEnum.NOTHING,
        bucketReferenceFirstUpdate.getMetadata().getCloningInstructions());
    // Attempt to update bucket reference but {@code userWithPartialAccess} does not have
    // access to the bucket with fine-grained access
    assertThrows(
        ApiException.class,
        () ->
            GcsBucketUtils.updateGcsBucketReference(
                partialAccessApi,
                getWorkspaceId(),
                bucketResourceId,
                /* name= */ null,
                /* description= */ null,
                fineGrainedBucket.getAttributes().getBucketName(),
                /* cloningInstructions= */ null));
    // Successfully update the referencing target because the {@code userWithFullAccess} has
    // access to the bucket with fine-grained access.
    GcpGcsBucketResource bucketReferenceSecondUpdate =
        GcsBucketUtils.updateGcsBucketReference(
            fullAccessApi,
            getWorkspaceId(),
            bucketResourceId,
            /* name= */ null,
            /* description= */ null,
            fineGrainedBucket.getAttributes().getBucketName(),
            /* cloningInstructions= */ null);
    assertEquals(newBucketName, bucketReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBucketDescription, bucketReferenceSecondUpdate.getMetadata().getDescription());
    assertEquals(
        fineGrainedBucket.getAttributes().getBucketName(),
        bucketReferenceSecondUpdate.getAttributes().getBucketName());

    // Update GCS bucket object's name and description
    String newBlobName = "newBlobName";
    String newBlobDescription = "a new description to the new bucket blob reference";
    GcpGcsObjectResource blobResource =
        GcsBucketUtils.updateGcsBucketObjectReference(
            fullAccessApi,
            getWorkspaceId(),
            fileResourceId,
            newBlobName,
            newBlobDescription,
            /* bucketName= */ null,
            /* objectName= */ null,
            CloningInstructionsEnum.NOTHING);
    assertEquals(newBlobName, blobResource.getMetadata().getName());
    assertEquals(newBlobDescription, blobResource.getMetadata().getDescription());
    assertEquals(gcsFileAttributes.getBucketName(), blobResource.getAttributes().getBucketName());
    assertEquals(gcsFileAttributes.getFileName(), blobResource.getAttributes().getFileName());
    assertEquals(
        CloningInstructionsEnum.NOTHING, blobResource.getMetadata().getCloningInstructions());
    // Update GCS bucket object's referencing target from foo/monkey_sees_monkey_dos.txt to foo/.

    assertTrue(partialAccessResourceApi.checkReferenceAccess(getWorkspaceId(), fileResourceId));

    // Update object path only.
    // Attempt to update to foo but {@code userWithPartialAccess} does not have access to foo/
    assertThrows(
        ApiException.class,
        () ->
            GcsBucketUtils.updateGcsBucketObjectReference(
                partialAccessApi,
                getWorkspaceId(),
                fileResourceId,
                /* name= */ null,
                /* description= */ null,
                gcsFileAttributes.getBucketName(),
                gcsFolderAttributes.getFileName(),
                /* cloningInstructions= */ null));
    // User with access to foo/ can successfully update the referencing target to foo/.
    GcpGcsObjectResource blobReferenceSecondUpdate =
        GcsBucketUtils.updateGcsBucketObjectReference(
            fullAccessApi,
            getWorkspaceId(),
            fileResourceId,
            /* name= */ null,
            /* description= */ null,
            /* bucketName= */ null,
            gcsFolderAttributes.getFileName(),
            /* cloningInstructions= */ null);
    assertEquals(
        gcsFileAttributes.getBucketName(),
        blobReferenceSecondUpdate.getAttributes().getBucketName());
    assertEquals(
        gcsFolderAttributes.getFileName(), blobReferenceSecondUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceSecondUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceSecondUpdate.getMetadata().getDescription());

    // update bucket only.
    GcpGcsObjectResource blobReferenceThirdUpdate =
        GcsBucketUtils.updateGcsBucketObjectReference(
            fullAccessApi,
            getWorkspaceId(),
            fileResourceId,
            /* name= */ null,
            /* description= */ null,
            /* bucketName= */ gcsUniformAccessBucketAttributes.getBucketName(),
            /* objectName= */ null,
            /* cloningInstructions= */ null);
    assertEquals(
        gcsUniformAccessBucketAttributes.getBucketName(),
        blobReferenceThirdUpdate.getAttributes().getBucketName());
    assertEquals(
        gcsFolderAttributes.getFileName(), blobReferenceThirdUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceThirdUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceThirdUpdate.getMetadata().getDescription());

    // Update both bucket and object path.
    GcpGcsObjectResource blobReferenceFourthUpdate =
        GcsBucketUtils.updateGcsBucketObjectReference(
            fullAccessApi,
            getWorkspaceId(),
            fileResourceId,
            /* name= */ null,
            /* description= */ null,
            /* bucketName= */ gcsFileAttributes.getBucketName(),
            gcsFileAttributes.getFileName(),
            /* cloningInstructions= */ null);
    assertEquals(
        gcsFileAttributes.getBucketName(),
        blobReferenceFourthUpdate.getAttributes().getBucketName());
    assertEquals(
        gcsFileAttributes.getFileName(), blobReferenceFourthUpdate.getAttributes().getFileName());
    assertEquals(newBlobName, blobReferenceFourthUpdate.getMetadata().getName());
    assertEquals(newBlobDescription, blobReferenceFourthUpdate.getMetadata().getDescription());
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    if (destinationWorkspaceId != null) {
      workspaceApi.deleteWorkspace(destinationWorkspaceId);
    }
  }
}
