package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.ControlledResourceMetadata;
import bio.terra.workspace.model.DataRepoSnapshotResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
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

  private ControlledGcpResourceApi controlledGcpResourceApi;
  private ReferencedGcpResourceApi referencedGcpResourceApi;
  private ResourceApi resourceApi;
  private List<ResourceMetadata> resourceList;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {

    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    // static assumptions
    assertThat(PAGE_SIZE * 2, lessThan(RESOURCE_COUNT));
    assertThat(PAGE_SIZE * 3, greaterThan(RESOURCE_COUNT));

    // REVIEWERS: Looking at all of these clients, I am wondering if our controller breakdown is
    // really what we want. It makes sense from the implementation of WSM server, but for the
    // client, having to mint all of these API objects could be an annoyance.
    // What do you all think?
    //
    // One idea: we could have a general controller, but have it just get the userRequest and
    // dispatch to controller layer classes that would do the API <-> internal conversions.
    ApiClient apiClient = ClientTestUtils.getClientForTestUser(testUsers.get(0), server);
    controlledGcpResourceApi = new ControlledGcpResourceApi(apiClient);
    referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);
    resourceApi = new ResourceApi(apiClient);

    // Create a cloud context for the workspace
    CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    // create the resources for the test
    logger.info("Creating {} resources", RESOURCE_COUNT);
    resourceList =
        makeResources(referencedGcpResourceApi, controlledGcpResourceApi, getWorkspaceId());
    logger.info("Created {} resources", resourceList.size());
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // TODO: when we have resource permissions in shape, test visibility:
    //  - private controlled resources

    // Case 1: fetch all
    ResourceList enumList =
        resourceApi.enumerateResources(getWorkspaceId(), 0, RESOURCE_COUNT, null, null);
    logResult("fetchall", enumList);
    // Make sure we got all of the expected ids
    matchFullResourceList(enumList.getResources());

    // Case 2: fetch by pages
    ResourceList page1List =
        resourceApi.enumerateResources(getWorkspaceId(), 0, PAGE_SIZE, null, null);
    logResult("page1", page1List);
    assertThat(page1List.getResources().size(), equalTo(PAGE_SIZE));
    ResourceList page2List =
        resourceApi.enumerateResources(getWorkspaceId(), PAGE_SIZE, PAGE_SIZE, null, null);
    logResult("page2", page2List);
    assertThat(page2List.getResources().size(), equalTo(PAGE_SIZE));
    ResourceList page3List =
        resourceApi.enumerateResources(getWorkspaceId(), 2 * PAGE_SIZE, PAGE_SIZE, null, null);
    logResult("page3", page3List);
    assertThat(page3List.getResources().size(), lessThan(PAGE_SIZE));

    List<ResourceDescription> descriptionList = new ArrayList<>();
    descriptionList.addAll(page1List.getResources());
    descriptionList.addAll(page2List.getResources());
    descriptionList.addAll(page3List.getResources());
    matchFullResourceList(descriptionList);

    // Case 3: no results if offset is too high
    ResourceList enumEmptyList =
        resourceApi.enumerateResources(getWorkspaceId(), 10 * PAGE_SIZE, PAGE_SIZE, null, null);
    assertThat(enumEmptyList.getResources().size(), equalTo(0));

    // Case 4: filter by resource type
    ResourceList snapshots =
        resourceApi.enumerateResources(
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
        resourceApi.enumerateResources(
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
        resourceApi.enumerateResources(
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
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // Delete the controlled buckets
    for (ResourceMetadata metadata : resourceList) {
      if (metadata.getStewardshipType() == StewardshipType.CONTROLLED
          && metadata.getResourceType() == ResourceType.GCS_BUCKET) {
        ResourceMaker.deleteControlledGcsBucket(
            metadata.getResourceId(), getWorkspaceId(), controlledGcpResourceApi);
      }
    }
    // Cleanup the workspace after we cleanup the the buckets!
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
      UUID workspaceId)
      throws Exception {

    List<ResourceMetadata> resourceList = new ArrayList<>();

    // We have four kinds of resources right now, so we switch on the modulus
    // of the counter to choose which kind to make. We make a random name so that
    // different runs will have different alphabetical order.
    for (int i = 0; i < RESOURCE_COUNT; i++) {
      String name = RandomStringUtils.random(6, true, false) + i;

      switch (i % 4) {
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
                    getDataRepoInstance());
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
            GcpGcsBucketResource resource =
                ResourceMaker.makeControlledGcsBucketUserShared(
                    controlledGcpResourceApi, workspaceId, name);
            resourceList.add(resource.getMetadata());
            break;
          }
      }
    }

    return resourceList;
  }
}
