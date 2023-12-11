package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
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
import bio.terra.workspace.model.ControlledResourceMetadata;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.RetryUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class EnumerateResources extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(EnumerateResources.class);

  // The test is written so that these can be modified here. The invariant is that the
  // resulting set of resources can be read in 3 pages where the third page is not full.
  // Number of resources to create for enumeration
  private static final int RESOURCE_COUNT = 7;
  // Page size to use for enumeration paging
  private static final int PAGE_SIZE = 3;

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

    // Add second user to the workspace as a reader
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), workspaceReader, IamRole.READER);

    // Create a cloud context for the workspace
    String gcpProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    // Wait for reader permissions to propagate
    ClientTestUtils.workspaceRoleWaitForPropagation(workspaceOwner, gcpProjectId);

    // create the resources for the test
    logger.info("Creating {} resources", RESOURCE_COUNT);
    resourceList =
        MultiResourcesUtils.makeResources(
            ownerReferencedGcpResourceApi, ownerControlledGcpResourceApi, getWorkspaceId());

    logger.info("Created {} resources", resourceList.size());
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

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
        RetryUtils.getWithRetryOnException(
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
    ResourceList buckets =
        ownerResourceApi.enumerateResources(
            getWorkspaceId(), 0, RESOURCE_COUNT, ResourceType.GCS_BUCKET, null);
    logResult("buckets", buckets);
    long expectedBuckets =
        resourceList.stream().filter(m -> m.getResourceType() == ResourceType.GCS_BUCKET).count();
    logger.info("Counted {} buckets created", expectedBuckets);
    // Note - assertThat exits out on an int -> long compare, so just don't do that.
    long actualBuckets = buckets.getResources().size();
    assertThat(actualBuckets, equalTo(expectedBuckets));

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
    TestUtils.assertContains(invalidPaginationException.getMessage(), "enumerateResources.offset");
    assertThat(invalidPaginationException.getCode(), equalTo(400));

    invalidPaginationException =
        assertThrows(
            ApiException.class,
            () ->
                ownerResourceApi.enumerateResources(
                    getWorkspaceId(), 0, 0, ResourceType.GCS_BUCKET, StewardshipType.CONTROLLED));
    TestUtils.assertContains(invalidPaginationException.getMessage(), "enumerateResources.limit");
    assertThat(invalidPaginationException.getCode(), equalTo(400));
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
}
