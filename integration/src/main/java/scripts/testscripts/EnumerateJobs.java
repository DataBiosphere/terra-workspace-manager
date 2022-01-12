package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.Alpha1Api;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.EnumerateJobsResult;
import bio.terra.workspace.model.EnumeratedJob;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.ResourceUnion;
import bio.terra.workspace.model.StewardshipType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ResourceMaker;

public class EnumerateJobs extends DataRepoTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(EnumerateJobs.class);

  // The test is written so that these can be modified here. The invariant is that the
  // resulting set of resources can be read in 3 pages where the third page is not full.
  // Number of resources to create for enumeration
  private static final int RESOURCE_COUNT = 12;
  // Page size to use for enumeration paging
  private static final int PAGE_SIZE = 4;
  // Roles to grant user on private resource
  private static final ImmutableList<ControlledResourceIamRole> PRIVATE_ROLES =
      ImmutableList.of(ControlledResourceIamRole.WRITER, ControlledResourceIamRole.EDITOR);

  private ControlledGcpResourceApi ownerControlledGcpResourceApi;
  private ReferencedGcpResourceApi ownerReferencedGcpResourceApi;
  private ResourceApi ownerResourceApi;
  private ResourceApi readerResourceApi;
  private Alpha1Api alpha1Api;
  private List<ResourceMetadata> resourceList;
  private TestUserSpecification workspaceReader;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {

    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    TestUserSpecification workspaceOwner = testUsers.get(0);

    // TODO: if we like the alpha1 API for job enumeration, then we can maybe piggyback on
    //  the EnumerateResources test instead of creating our own set.

    ApiClient ownerApiClient = ClientTestUtils.getClientForTestUser(workspaceOwner, server);
    ownerControlledGcpResourceApi = new ControlledGcpResourceApi(ownerApiClient);
    ownerReferencedGcpResourceApi = new ReferencedGcpResourceApi(ownerApiClient);
    ownerResourceApi = new ResourceApi(ownerApiClient);
    alpha1Api = new Alpha1Api(ownerApiClient);

    // Create a cloud context for the workspace
    CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    // create the resources for the test
    logger.info("Creating {} resources", RESOURCE_COUNT);
    resourceList =
        ResourceMaker.makeResources(
            ownerReferencedGcpResourceApi,
            ownerControlledGcpResourceApi,
            getWorkspaceId(),
            getDataRepoSnapshotId(),
            getDataRepoInstanceName(),
            RESOURCE_COUNT);

    logger.info("Created {} resources", resourceList.size());
    logger.info("Cleaning up {} resources", resourceList.size());

    ResourceMaker.cleanupResources(resourceList, ownerControlledGcpResourceApi, getWorkspaceId());
    logger.info("Cleaned up {} resources", resourceList.size());
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

    // Case 1: fetch all
    EnumerateJobsResult fetchall =
        alpha1Api.enumerateJobs(getWorkspaceId(), null, null, null, null, null, null);
    logResult("fetchall", fetchall);

    // Case 2: fetch by pages
    String pageToken = null;
    for (int pageCount = 1; true; pageCount++) {
      EnumerateJobsResult page =
          alpha1Api.enumerateJobs(getWorkspaceId(), PAGE_SIZE, pageToken, null, null, null, null);
      logResult("page " + pageCount, page);
      pageToken = page.getPageToken();
      if (page.getResults().size() == 0) {
        break;
      }
    }

    // Case 4: filter by resource type
    EnumerateJobsResult snapshots =
        alpha1Api.enumerateJobs(
            getWorkspaceId(), null, null, ResourceType.DATA_REPO_SNAPSHOT, null, null, null);
    logResult("snapshots", snapshots);

    // Case 5: filter by stewardship type
    EnumerateJobsResult controlled =
        alpha1Api.enumerateJobs(
            getWorkspaceId(), null, null, null, StewardshipType.CONTROLLED, null, null);
    logResult("controlled", controlled);

    // Case 6: filter by resource and stewardship
    EnumerateJobsResult controlledBuckets =
        alpha1Api.enumerateJobs(
            getWorkspaceId(),
            null,
            null,
            ResourceType.GCS_BUCKET,
            StewardshipType.CONTROLLED,
            null,
            null);
    logResult("controlledBuckets", controlledBuckets);

    // Case 7: validate error on invalid pagination params
    ApiException invalidPaginationException =
        assertThrows(
            ApiException.class,
            () -> alpha1Api.enumerateJobs(getWorkspaceId(), -5, null, null, null, null, null));
    assertThat(invalidPaginationException.getMessage(), containsString("Invalid pagination"));

    invalidPaginationException =
        assertThrows(
            ApiException.class,
            () ->
                alpha1Api.enumerateJobs(getWorkspaceId(), 22, "junktoken", null, null, null, null));
    assertThat(invalidPaginationException.getMessage(), containsString("Invalid page token"));
  }

  private void logResult(String tag, EnumerateJobsResult result) {
    logger.info(
        "EnumerateJobs results for {} - {} of {} next {}",
        tag,
        result.getResults().size(),
        result.getTotalResults(),
        result.getPageToken());

    for (EnumeratedJob job : result.getResults()) {
      logger.info(
          " jobid={} status={} submitted={} completed={} code={} operation={} {}",
          job.getJobReport().getId(),
          job.getJobReport().getStatus(),
          job.getJobReport().getSubmitted(),
          Optional.ofNullable(job.getJobReport().getCompleted()).orElse("<>"),
          job.getJobReport().getStatusCode().toString(),
          job.getOperationType(),
          resourceString(job));
    }
  }

  private String resourceString(EnumeratedJob job) {
    ResourceMetadata metadata = null;

    if (job.getResourceType() != null && job.getResource() != null) {
      ResourceUnion union = job.getResource();
      if (union.getGcpBqDataset() != null) {
        metadata = union.getGcpBqDataset().getMetadata();
      } else if (union.getGcpBqDataTable() != null) {
        metadata = union.getGcpBqDataTable().getMetadata();
      } else if (union.getGcpDataRepoSnapshot() != null) {
        metadata = union.getGcpDataRepoSnapshot().getMetadata();
      } else if (union.getGcpGcsBucket() != null) {
        metadata = union.getGcpGcsBucket().getMetadata();
      } else if (union.getGcpGcsObject() != null) {
        metadata = union.getGcpGcsObject().getMetadata();
      }
    }

    if (metadata == null) {
      return "<>";
    }
    return String.format(
        " resource: type=%s name=%s id=%s",
        job.getResourceType().toString(), metadata.getName(), metadata.getResourceId());
  }
}
