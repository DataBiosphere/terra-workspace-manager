package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

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
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GitRepoAttributes;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.ResourceUnion;
import bio.terra.workspace.model.StewardshipType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataRepoTestScriptBase;
import scripts.utils.ParameterUtils;
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
  private GcpGcsBucketAttributes referenceBucketAttributes;
  private GcpBigQueryDataTableAttributes referenceBqTableAttributes;
  private GitRepoAttributes referenceGitRepoAttributes;
  private ResourceApi ownerResourceApi;
  private ResourceApi readerResourceApi;
  private Alpha1Api alpha1Api;
  private List<ResourceMetadata> resourceList;
  private TestUserSpecification workspaceReader;

  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);
    referenceBucketAttributes = ParameterUtils.getFineGrainedBucketReference(parameters);
    referenceBqTableAttributes = ParameterUtils.getBigQueryDataTableReference(parameters);
    referenceGitRepoAttributes = ParameterUtils.getSshGitRepoReference(parameters);
  }

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {

    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    TestUserSpecification workspaceOwner = testUsers.get(0);

    // If we like the alpha1 API for job enumeration, then we can maybe piggyback on
    // the EnumerateResources test instead of creating our own set.

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
            referenceBucketAttributes,
            referenceBqTableAttributes,
            referenceGitRepoAttributes,
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
    // TODO: [PF-1281] we need another type of filtering to be able to do better validation of the
    // result return.

    // Case 2: fetch by pages
    String pageToken = null;
    for (int pageCount = 1; true; pageCount++) {
      EnumerateJobsResult page =
          alpha1Api.enumerateJobs(getWorkspaceId(), PAGE_SIZE, pageToken, null, null, null, null);
      logResult("page " + pageCount, page);
      assertThat(
          "Not more than page size items returned",
          page.getResults().size(),
          lessThanOrEqualTo(PAGE_SIZE));

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
    for (EnumeratedJob job : snapshots.getResults()) {
      assertThat(
          "Job is a snapshot", job.getResourceType(), equalTo(ResourceType.DATA_REPO_SNAPSHOT));
      assertNotNull(job.getResource().getGcpDataRepoSnapshot(), "Snapshot resource present");
      assertThat(
          "Resource is a snapshot",
          job.getResource().getGcpDataRepoSnapshot().getMetadata().getResourceType(),
          equalTo(ResourceType.DATA_REPO_SNAPSHOT));
    }

    // Case 5: filter by stewardship type
    EnumerateJobsResult controlled =
        alpha1Api.enumerateJobs(
            getWorkspaceId(), null, null, null, StewardshipType.CONTROLLED, null, null);
    logResult("controlled", controlled);
    for (EnumeratedJob job : controlled.getResults()) {
      ResourceMetadata metadata = getResourceMetadata(job);
      assertNotNull(metadata, "Resource has metadata");
      assertThat(
          "Resource is controlled",
          metadata.getStewardshipType(),
          equalTo(StewardshipType.CONTROLLED));
    }

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
    for (EnumeratedJob job : controlledBuckets.getResults()) {
      ResourceMetadata metadata = getResourceMetadata(job);
      assertNotNull(metadata, "Resource has metadata");
      assertThat(
          "Resource is controlled",
          metadata.getStewardshipType(),
          equalTo(StewardshipType.CONTROLLED));
      assertThat(
          "Resource is a bucket", metadata.getResourceType(), equalTo(ResourceType.GCS_BUCKET));
    }

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

  private ResourceMetadata getResourceMetadata(EnumeratedJob job) {
    if (job.getResourceType() == null || job.getResource() == null) {
      return null;
    }
    ResourceUnion union = job.getResource();
    switch (job.getResourceType()) {
      case AI_NOTEBOOK:
        return union.getGcpAiNotebookInstance().getMetadata();

      case DATA_REPO_SNAPSHOT:
        return union.getGcpDataRepoSnapshot().getMetadata();

      case GCS_BUCKET:
        return union.getGcpGcsBucket().getMetadata();

      case GCS_OBJECT:
        return union.getGcpGcsObject().getMetadata();

      case BIG_QUERY_DATASET:
        return union.getGcpBqDataset().getMetadata();

      case BIG_QUERY_DATA_TABLE:
        return union.getGcpBqDataTable().getMetadata();

      case AZURE_IP:
        return union.getAzureIp().getMetadata();

      case AZURE_DISK:
        return union.getAzureDisk().getMetadata();

      case AZURE_NETWORK:
        return union.getAzureNetwork().getMetadata();
      case AZURE_VM:
        return union.getAzureVm().getMetadata();

      case AZURE_STORAGE_ACCOUNT:
        return union.getAzureStorageAccount().getMetadata();

      default:
        fail("Unknown resource type: " + job.getResourceType());
    }

    return null;
  }

  private String resourceString(EnumeratedJob job) {
    ResourceMetadata metadata = getResourceMetadata(job);
    if (metadata == null) {
      return "<>";
    }
    return String.format(
        " resource: type=%s name=%s id=%s",
        job.getResourceType().toString(), metadata.getName(), metadata.getResourceId());
  }
}
