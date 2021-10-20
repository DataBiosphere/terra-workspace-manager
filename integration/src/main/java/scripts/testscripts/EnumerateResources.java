package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.ControlledResourceMetadata;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class EnumerateResources extends DataRepoTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(EnumerateResources.class);

  // TODO: make these parameters in the test description?
  // The test is written so that these can be modified here. The invariant is that the
  // resulting set of resources can be read in 3 pages where the third page is not full.
  // Number of resources to create for enumeration
  private static final int RESOURCE_COUNT = 10;
  // Page size to use for enumeration paging
  private static final int PAGE_SIZE = 4;
  // Roles to grant user on private resource
  private static final ImmutableList<ControlledResourceIamRole> PRIVATE_ROLES =
      ImmutableList.of(ControlledResourceIamRole.WRITER, ControlledResourceIamRole.EDITOR);

  private ControlledGcpResourceApi ownerControlledGcpResourceApi;
  private ReferencedGcpResourceApi ownerReferencedGcpResourceApi;
  private ResourceApi ownerResourceApi;
  private ResourceApi readerResourceApi;
  private List<ResourceMetadata> resourceList;
  private TestUserSpecification workspaceReader;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {

    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    assertThat(
        "There must be two test users defined for this test.",
        testUsers != null && testUsers.size() == 2);
    TestUserSpecification workspaceOwner = testUsers.get(0);
    workspaceReader = testUsers.get(1);

    // static assumptions
    assertThat(PAGE_SIZE * 2, lessThan(RESOURCE_COUNT));
    assertThat(PAGE_SIZE * 3, greaterThan(RESOURCE_COUNT));

    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(workspaceOwner, server);
    ownerControlledGcpResourceApi = new ControlledGcpResourceApi(ownerApiClient);
    ownerReferencedGcpResourceApi = new ReferencedGcpResourceApi(ownerApiClient);
    ownerResourceApi = new ResourceApi(ownerApiClient);

    ApiClient readerApiClient = ClientTestUtils.getClientForTestUser(workspaceReader, server);
    readerResourceApi = new ResourceApi(readerApiClient);

    // Create a cloud context for the workspace
    CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    // create the resources for the test
    logger.info("Creating {} resources", RESOURCE_COUNT);
    resourceList =
        makeResources(
            ownerReferencedGcpResourceApi,
            ownerControlledGcpResourceApi,
            getWorkspaceId(),
            workspaceOwner.userEmail);
    logger.info("Created {} resources", resourceList.size());
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    // Add second user to the workspace as a reader
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(workspaceReader.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // Case 1: fetch all
    ResourceList enumList =
        ownerResourceApi.enumerateResources(getWorkspaceId(), 0, RESOURCE_COUNT, null, null);
    logResult("fetchall", enumList);
    // Make sure we got all of the expected ids
    matchFullResourceList(enumList.getResources());

    // Repeat case 1 as the workspace reader.
    // As this is the first operation after modifying workspace IAM groups, retry here to compensate
    // for the delay in GCP IAM propagation.
    ResourceList readerEnumList =
        ClientTestUtils.getWithRetryOnException(
            () ->
                readerResourceApi.enumerateResources(
                    getWorkspaceId(), 0, RESOURCE_COUNT, null, null));
    logResult("fetchall reader", readerEnumList);
    matchFullResourceList(readerEnumList.getResources());

    // Case 2: fetch by pages
    ResourceList page1List =
        ownerResourceApi.enumerateResources(getWorkspaceId(), 0, PAGE_SIZE, null, null);
    logResult("page1", page1List);
    assertThat(page1List.getResources().size(), equalTo(PAGE_SIZE));
    ResourceList page2List =
        ownerResourceApi.enumerateResources(getWorkspaceId(), PAGE_SIZE, PAGE_SIZE, null, null);
    logResult("page2", page2List);
    assertThat(page2List.getResources().size(), equalTo(PAGE_SIZE));
    ResourceList page3List =
        ownerResourceApi.enumerateResources(getWorkspaceId(), 2 * PAGE_SIZE, PAGE_SIZE, null, null);
    logResult("page3", page3List);
    assertThat(page3List.getResources().size(), lessThan(PAGE_SIZE));

    List<ResourceDescription> descriptionList = new ArrayList<>();
    descriptionList.addAll(page1List.getResources());
    descriptionList.addAll(page2List.getResources());
    descriptionList.addAll(page3List.getResources());
    matchFullResourceList(descriptionList);

    // Case 3: no results if offset is too high
    ResourceList enumEmptyList =
        ownerResourceApi.enumerateResources(
            getWorkspaceId(), 10 * PAGE_SIZE, PAGE_SIZE, null, null);
    assertThat(enumEmptyList.getResources().size(), equalTo(0));

    // Case 4: filter by resource type
    ResourceList snapshots =
        ownerResourceApi.enumerateResources(
            getWorkspaceId(), 0, RESOURCE_COUNT, ResourceType.DATA_REPO_SNAPSHOT, null);
    logResult("snapshots", snapshots);
    long expectedSnapshots =
        resourceList.stream()
            .filter(m -> m.getResourceType() == ResourceType.DATA_REPO_SNAPSHOT)
            .count();
    logger.info("Counted {} snapshots created", expectedSnapshots);
    // Note - assertThat exits out on an int -> long compare, so just don't do that.
    long actualSnapshots = snapshots.getResources().size();
    assertThat(actualSnapshots, equalTo(expectedSnapshots));

    // Case 5: filter by stewardship type
    ResourceList referencedList =
        ownerResourceApi.enumerateResources(
            getWorkspaceId(), 0, RESOURCE_COUNT, null, StewardshipType.REFERENCED);
    logResult("referenced", referencedList);
    long expectedReferenced =
        resourceList.stream()
            .filter(m -> m.getStewardshipType() == StewardshipType.REFERENCED)
            .count();
    logger.info("Counted {} referenced created", expectedReferenced);
    long actualReferenced = referencedList.getResources().size();
    assertThat(actualReferenced, equalTo(expectedReferenced));

    // Case 6: filter by resource and stewardship
    ResourceList controlledBucketList =
        ownerResourceApi.enumerateResources(
            getWorkspaceId(),
            0,
            RESOURCE_COUNT,
            ResourceType.GCS_BUCKET,
            StewardshipType.CONTROLLED);
    logResult("controlledBucket", controlledBucketList);
    long expectedControlledBuckets =
        resourceList.stream()
            .filter(
                m ->
                    (m.getStewardshipType() == StewardshipType.CONTROLLED
                        && m.getResourceType() == ResourceType.GCS_BUCKET))
            .count();
    logger.info("Counted {} controlled buckets created", expectedControlledBuckets);
    long actualControlledBuckets = controlledBucketList.getResources().size();
    assertThat(actualControlledBuckets, equalTo(expectedControlledBuckets));

    // Case 7: validate error on invalid pagination params
    ApiException invalidPaginationException =
        assertThrows(
            ApiException.class,
            () ->
                ownerResourceApi.enumerateResources(
                    getWorkspaceId(), -11, 2, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED));
    assertThat(invalidPaginationException.getMessage(), containsString("Invalid pagination"));

    invalidPaginationException =
        assertThrows(
            ApiException.class,
            () ->
                ownerResourceApi.enumerateResources(
                    getWorkspaceId(), 0, 0, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED));
    assertThat(invalidPaginationException.getMessage(), containsString("Invalid pagination"));
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // Delete the controlled resources
    for (ResourceMetadata metadata : resourceList) {
      if (metadata.getStewardshipType() == StewardshipType.CONTROLLED) {
        switch (metadata.getResourceType()) {
          case GCS_BUCKET:
            ResourceMaker.deleteControlledGcsBucket(
                metadata.getResourceId(), getWorkspaceId(), ownerControlledGcpResourceApi);
            break;
          case BIG_QUERY_DATASET:
            ownerControlledGcpResourceApi.deleteBigQueryDataset(
                getWorkspaceId(), metadata.getResourceId());
            break;
          case AI_NOTEBOOK:
          case DATA_REPO_SNAPSHOT:
          default:
            throw new IllegalStateException(
                String.format(
                    "No cleanup method specified for resource type %s in test EnumerateResources.",
                    metadata.getResourceType()));
        }
      }
    }
    // Cleanup the workspace after we cleanup the the resources!
    super.doCleanup(testUsers, workspaceApi);
  }

  private void logResult(String tag, ResourceList resourceList) {
    List<ResourceDescription> descList = resourceList.getResources();
    logger.info("Enumeration results for {} - {} resources", tag, descList.size());
    for (ResourceDescription desc : descList) {
      ResourceMetadata metadata = desc.getMetadata();

      String access = "<null>";
      String managed = "<null>";
      String user = "<null>";
      if (metadata.getStewardshipType() == StewardshipType.CONTROLLED
          && metadata.getControlledResourceMetadata() != null) {
        ControlledResourceMetadata controlled = metadata.getControlledResourceMetadata();
        access = controlled.getAccessScope().toString();
        managed = controlled.getManagedBy().toString();
        user = controlled.getPrivateResourceUser().getUserName();
      }
      logger.info(
          "  {}: id={} type={} stew={} cloud={} access={} managed={} user={}",
          metadata.getName(),
          metadata.getResourceId(),
          metadata.getResourceType(),
          metadata.getStewardshipType(),
          metadata.getCloudPlatform(),
          access,
          managed,
          user);
    }
  }

  private void matchFullResourceList(List<ResourceDescription> enumList) {
    assertThat(enumList.size(), equalTo(RESOURCE_COUNT));
    List<UUID> resourceListIds =
        resourceList.stream().map(ResourceMetadata::getResourceId).collect(Collectors.toList());
    List<UUID> enumListIds =
        enumList.stream().map(r -> r.getMetadata().getResourceId()).collect(Collectors.toList());
    resourceListIds.retainAll(enumListIds);
    assertThat(resourceListIds.size(), equalTo(RESOURCE_COUNT));
    logger.info("Successfully matched all resources");
  }

  private List<ResourceMetadata> makeResources(
      ReferencedGcpResourceApi referencedGcpResourceApi,
      ControlledGcpResourceApi controlledGcpResourceApi,
      UUID workspaceId,
      String testUserEmail)
      throws Exception {

    List<ResourceMetadata> resourceList = new ArrayList<>();

    // We have five kinds of resources right now, so we switch on the modulus
    // of the counter to choose which kind to make. We make a random name so that
    // different runs will have different alphabetical order.
    for (int i = 0; i < RESOURCE_COUNT; i++) {
      String name = RandomStringUtils.random(6, true, false) + i;

      switch (i % 5) {
        case 0:
          {
            GcpBigQueryDatasetResource resource =
                ResourceMaker.makeBigQueryReference(referencedGcpResourceApi, workspaceId, name);
            resourceList.add(resource.getMetadata());
            break;
          }

        case 1:
          {
            DataRepoSnapshotResource resource =
                ResourceMaker.makeDataRepoSnapshotReference(
                    referencedGcpResourceApi,
                    workspaceId,
                    name,
                    getDataRepoSnapshotId(),
                    getDataRepoInstanceName());
            resourceList.add(resource.getMetadata());
            break;
          }

        case 2:
          {
            GcpGcsBucketResource resource =
                ResourceMaker.makeGcsBucketReference(referencedGcpResourceApi, workspaceId, name);
            resourceList.add(resource.getMetadata());
            break;
          }

        case 3:
          {
            PrivateResourceIamRoles privateRoles = new PrivateResourceIamRoles();
            privateRoles.addAll(PRIVATE_ROLES);
            GcpGcsBucketResource resource =
                ResourceMaker.makeControlledGcsBucketUserPrivate(
                        controlledGcpResourceApi, workspaceId, name, testUserEmail, privateRoles)
                    .getGcpBucket();
            resourceList.add(resource.getMetadata());
            break;
          }

        case 4:
          {
            GcpBigQueryDatasetResource resource =
                ResourceMaker.makeControlledBigQueryDatasetUserShared(
                    controlledGcpResourceApi, workspaceId, name);
            resourceList.add(resource.getMetadata());
            break;
          }
      }
    }

    return resourceList;
  }
}
