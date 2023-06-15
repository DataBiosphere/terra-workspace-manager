package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_RESOURCE_PROPERTIES;
import static bio.terra.workspace.db.WorkspaceActivityLogDao.ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloneControlledFlexibleResourceRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledFlexibleResourceResult;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpBigQueryDataTableResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpBigQueryDatasetResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpDataRepoSnapshotResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpGcsObjectResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGitRepoResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiControlledFlexibleResourceCreationParameters;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateCloudContextResult;
import bio.terra.workspace.generated.model.ApiCreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGceInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledFlexibleResource;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGceInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResourceAttributes;
import bio.terra.workspace.generated.model.ApiFlexibleResourceUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
import bio.terra.workspace.generated.model.ApiGitRepoAttributes;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiPropertyKeys;
import bio.terra.workspace.generated.model.ApiRegions;
import bio.terra.workspace.generated.model.ApiResourceDescription;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceLineageEntry;
import bio.terra.workspace.generated.model.ApiResourceList;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiState;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateMode;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateRequest;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CompleteTransferOperationStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.RemoveBucketRolesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetBucketRolesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetReferencedDestinationGcsBucketInWorkingMapStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetReferencedDestinationGcsBucketResponseStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.TransferGcsBucketToGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CompleteTableCopyJobsStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CreateTableCopyJobsStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetReferencedDestinationBigQueryDatasetInWorkingMapStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetReferencedDestinationBigQueryDatasetResponseStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceMetadataStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A collection of utilities and constants useful for MockMVC-based tests. This style of tests lets
 * us test controller-layer code (request/response parsing, authz, and validation) without actually
 * spinning up a local server.
 *
 * <p>All methods must work for unit and connected tests. Take in AuthenticatedUserRequest
 * parameter; don't hard-code AuthenticatedUserRequest.
 *
 * <p>TODO: it's probably worth looking into whether we can automatically pull routes from the
 * generated swagger, instead of manually wrapping them here.
 */
@Component
public class MockMvcUtils {
  public static final String AUTH_HEADER = "Authorization";
  public static final String WORKSPACES_V1_PATH = "/api/workspaces/v1";
  public static final String WORKSPACES_V1_BY_UUID_PATH_FORMAT = "/api/workspaces/v1/%s";
  public static final String WORKSPACES_V1_BY_UFID_PATH_FORMAT =
      "/api/workspaces/v1/workspaceByUserFacingId/%s";
  public static final String ADD_USER_TO_WORKSPACE_PATH_FORMAT =
      "/api/workspaces/v1/%s/roles/%s/members";
  public static final String CLONE_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1/%s/clone";
  public static final String CLONE_WORKSPACE_RESULT_PATH_FORMAT =
      "/api/workspaces/v1/%s/clone-result/%s";
  public static final String UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/properties";
  public static final String UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/policies";
  public static final String WORKSPACES_V1_EXPLAIN_POLICIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/policies/explain";
  public static final String WORKSPACES_V1_MERGE_CHECK_POLICIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/policies/mergeCheck";
  public static final String WORKSPACES_V1_LIST_VALID_REGIONS_PATH_FORMAT =
      "/api/workspaces/v1/%s/listValidRegions";
  public static final String GRANT_ROLE_PATH_FORMAT = "/api/workspaces/v1/%s/roles/%s/members";
  public static final String REMOVE_ROLE_PATH_FORMAT = "/api/workspaces/v1/%s/roles/%s/members/%s";
  public static final String RESOURCES_PATH_FORMAT = "/api/workspaces/v1/%s/resources";
  public static final String CREATE_SNAPSHOT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String CREATE_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts";
  public static final String DELETE_GCP_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts/GCP";
  public static final String GET_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts/result/%s";
  public static final String CREATE_AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks";
  public static final String CREATE_AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm";
  public static final String CREATE_AZURE_SAS_TOKEN_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s/getSasToken";
  public static final String CREATE_AZURE_BATCH_POOL_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/batchpool";
  public static final String CREATE_AZURE_STORAGE_CONTAINERS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer";
  public static final String AZURE_BATCH_POOL_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/batchpool/%s";
  public static final String AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks/%s";
  public static final String AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s";
  public static final String AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm/%s";
  public static final String CLONE_AZURE_STORAGE_CONTAINER_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s/clone";
  public static final String CREATE_AWS_STORAGE_FOLDERS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/storageFolder";
  public static final String AWS_STORAGE_FOLDERS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/storageFolder/%s";
  public static final String CREATE_AWS_SAGEMAKER_NOTEBOOKS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/notebook";
  public static final String AWS_SAGEMAKER_NOTEBOOKS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/aws/notebook/%s";
  public static final String GET_REFERENCED_GCP_GCS_BUCKET_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets/%s";
  public static final String CLONE_CONTROLLED_GCP_GCS_BUCKET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/%s/clone";
  public static final String CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/clone-result/%s";
  public static final String GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/generateName";
  public static final String GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/generateName";
  public static final String GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/generateName";
  public static final String GENERATE_GCP_GCE_INSTANCE_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/gce-instances/generateName";
  public static final String FOLDERS_V1_PATH_FORMAT = "/api/workspaces/v1/%s/folders";
  public static final String FOLDER_V1_PATH_FORMAT = "/api/workspaces/v1/%s/folders/%s";
  public static final String FOLDER_PROPERTIES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/folders/%s/properties";
  public static final String RESOURCE_PROPERTIES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/%s/properties";
  public static final String CONTROLLED_GCP_AI_NOTEBOOKS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances";
  public static final String CONTROLLED_GCP_AI_NOTEBOOK_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/%s";
  public static final String CONTROLLED_GCP_AI_NOTEBOOKS_V1_RESULT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/create-result/%s";
  public static final String CONTROLLED_GCP_GCE_INSTANCES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/gce-instances";
  public static final String CONTROLLED_GCP_GCE_INSTANCE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/gce-instances/%s";
  public static final String CONTROLLED_GCP_GCE_INSTANCES_V1_RESULT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/gce-instances/create-result/%s";
  public static final String CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets";
  public static final String CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/%s";
  public static final String CLONE_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/%s/clone";
  public static final String CLONE_RESULT_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/clone-result/%s";
  public static final String CONTROLLED_GCP_GCS_BUCKETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets";
  public static final String CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/%s";
  public static final String REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String REFERENCED_DATA_REPO_SNAPSHOT_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots/%s";
  public static final String CLONE_REFERENCED_DATA_REPO_SNAPSHOT_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots/%s/clone";
  public static final String REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets";
  public static final String REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets/%s";
  public static final String CLONE_REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets/%s/clone";
  public static final String REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bucket/objects";
  public static final String REFERENCED_GCP_GCS_OBJECT_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bucket/objects/%s";
  public static final String CLONE_REFERENCED_GCP_GCS_OBJECT_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bucket/objects/%s/clone";
  public static final String REFERENCED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets";
  public static final String REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets/%s";
  public static final String CLONE_REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets/%s/clone";
  public static final String REFERENCED_GCP_BIG_QUERY_DATA_TABLES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatatables";
  public static final String REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatatables/%s";
  public static final String CLONE_REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatatables/%s/clone";
  public static final String REFERENCED_GIT_REPOS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gitrepos";
  public static final String REFERENCED_GIT_REPO_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gitrepos/%s";
  public static final String CLONE_REFERENCED_GIT_REPO_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gitrepos/%s/clone";
  public static final String DELETE_FOLDER_JOB_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/folders/%s/result/%s";
  public static final String CONTROLLED_FLEXIBLE_RESOURCES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/any/flexibleResources";
  public static final String CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/any/flexibleResources/%s";
  public static final String CLONE_CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/any/flexibleResources/%s/clone";
  public static final String UPDATE_POLICIES_PATH_FORMAT = "/api/workspaces/v1/%s/policies";
  public static final String POLICY_V1_GET_REGION_INFO_PATH = "/api/policies/v1/getLocationInfo";

  public static final String LOAD_SIGNED_URL_LIST_PATH_FORMAT =
      "/api/workspaces/alpha1/%s/resources/controlled/gcp/buckets/%s/load";

  public static final String LOAD_SIGNED_URL_LIST_RESULT_PATH_FORMAT =
      "/api/workspaces/alpha1/%s/resources/controlled/gcp/buckets/%s/load/result/%s";
  public static final String DEFAULT_USER_EMAIL = "fake@gmail.com";
  public static final String DEFAULT_USER_SUBJECT_ID = "subjectId123456";
  // Only use this if you are mocking SAM. If you're using real SAM,
  // use userAccessUtils.defaultUserAuthRequest() instead.
  public static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          DEFAULT_USER_EMAIL, DEFAULT_USER_SUBJECT_ID, Optional.of("ThisIsNotARealBearerToken"));
  public static final String DEFAULT_GCP_RESOURCE_REGION = "us-central1";
  private static final Logger logger = LoggerFactory.getLogger(MockMvcUtils.class);
  private static final String DEST_BUCKET_RESOURCE_NAME =
      TestUtils.appendRandomNumber("i-am-the-cloned-bucket");

  private static final List<Integer> JOB_SUCCESS_CODES =
      List.of(HttpStatus.SC_OK, HttpStatus.SC_ACCEPTED);

  // Do not Autowire UserAccessUtils. UserAccessUtils are for connected tests and not unit tests
  // (since unit tests don't use real SAM). Instead, each method must take in userRequest.
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JobService jobService;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private SamService samService;

  public static MockHttpServletRequestBuilder addAuth(
      MockHttpServletRequestBuilder request, AuthenticatedUserRequest userRequest) {
    return request.header(AUTH_HEADER, "Bearer " + userRequest.getRequiredToken());
  }

  public static MockHttpServletRequestBuilder addJsonContentType(
      MockHttpServletRequestBuilder request) {
    return request.contentType("application/json");
  }

  public ApiWorkspaceDescription getWorkspace(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(userRequest, WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId);
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  /**
   * A method for cleaning up test workspaces. It checks for null workspaceId, and for the existence
   * of the workspace, before deleting.
   *
   * @param userRequest user doing the deleting
   * @param workspaceId workspace to delete
   * @throws Exception as usual in tests
   */
  public void cleanupWorkspace(AuthenticatedUserRequest userRequest, @Nullable UUID workspaceId)
      throws Exception {
    if (workspaceId == null) {
      return;
    }

    // Check if the workspace is already gone. Don't issue a failing delete if it is gone.
    int status = deleteWorkspaceNoCheck(userRequest, workspaceId);
    if (status == HttpServletResponse.SC_NOT_FOUND || status == HttpServletResponse.SC_NO_CONTENT) {
      return;
    }

    logger.error("Failed to cleanup workspace {}", workspaceId);
  }

  public ApiCloneWorkspaceResult cloneWorkspace(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      String spendProfile,
      @Nullable ApiWsmPolicyInputs policiesToAdd,
      @Nullable UUID destinationWorkspaceId)
      throws Exception {
    ApiCloneWorkspaceRequest request =
        new ApiCloneWorkspaceRequest()
            .destinationWorkspaceId(destinationWorkspaceId)
            .spendProfile(spendProfile)
            .additionalPolicies(policiesToAdd);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CLONE_WORKSPACE_PATH_FORMAT,
            sourceWorkspaceId,
            objectMapper.writeValueAsString(request));
    ApiCloneWorkspaceResult cloneWorkspace =
        objectMapper.readValue(serializedResponse, ApiCloneWorkspaceResult.class);
    if (destinationWorkspaceId == null) {
      destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    }

    // Wait for the clone to complete
    String jobId = cloneWorkspace.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(cloneWorkspace.getJobReport())) {
      TimeUnit.SECONDS.sleep(5);
      cloneWorkspace = getCloneWorkspaceResult(userRequest, destinationWorkspaceId, jobId);
    }
    assertEquals(ApiJobReport.StatusEnum.SUCCEEDED, cloneWorkspace.getJobReport().getStatus());

    return cloneWorkspace;
  }

  public ApiCreatedWorkspace createWorkspaceWithCloudContext(AuthenticatedUserRequest userRequest)
      throws Exception {
    ApiCreatedWorkspace createdWorkspace = createWorkspaceWithoutCloudContext(userRequest);
    createGcpCloudContextAndWait(userRequest, createdWorkspace.getId());
    return createdWorkspace;
  }

  public ApiCreatedWorkspace createWorkspaceWithPolicy(
      AuthenticatedUserRequest userRequest, ApiWsmPolicyInputs policy) throws Exception {
    ApiCreateWorkspaceRequestBody request =
        WorkspaceFixtures.createWorkspaceRequestBody().policies(policy);

    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH).content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public ApiCreatedWorkspace createWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest) throws Exception {
    return createWorkspaceWithoutCloudContext(userRequest, ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public ApiCreatedWorkspace createWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest, ApiWorkspaceStageModel stageModel)
      throws Exception {

    ApiCreateWorkspaceRequestBody request =
        WorkspaceFixtures.createWorkspaceRequestBody(stageModel);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest, WORKSPACES_V1_PATH, objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public ApiCreatedWorkspace createdWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest, ApiCreateWorkspaceRequestBody request)
      throws Exception {
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest, WORKSPACES_V1_PATH, objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public ApiErrorReport createWorkspaceWithoutCloudContextExpectError(
      @Nullable AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable ApiWorkspaceStageModel stageModel,
      @Nullable ApiWsmPolicyInputs policyInputs,
      int expectedCode)
      throws Exception {
    ApiCreateWorkspaceRequestBody request =
        WorkspaceFixtures.createWorkspaceRequestBody().id(workspaceId);
    if (stageModel != null) {
      request.stage(stageModel);
    }
    if (policyInputs != null) {
      request.policies(policyInputs);
    }
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH).content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(expectedCode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    try {
      ApiErrorReport errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
      assertEquals(expectedCode, errorReport.getStatusCode());
      return errorReport;
    } catch (Exception e) {
      // There is no ApiErrorReport to return
      return null;
    }
  }

  public void createGcpCloudContextAndWait(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    ApiCreateCloudContextResult result = createGcpCloudContext(userRequest, workspaceId);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(15);
      result = getCreateCloudContextResult(userRequest, workspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Created project %s for workspace %s"
            .formatted(result.getGcpContext().getProjectId(), workspaceId));
  }

  private ApiCreateCloudContextResult createGcpCloudContext(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(ApiCloudPlatform.GCP)
            .jobControl(new ApiJobControl().id(jobId));
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CREATE_CLOUD_CONTEXT_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  private ApiCreateCloudContextResult getCreateCloudContextResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, GET_CLOUD_CONTEXT_PATH_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  public void deleteGcpCloudContext(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                delete(DELETE_GCP_CLOUD_CONTEXT_PATH_FORMAT.formatted(workspaceId)), userRequest))
        .andExpect(status().isNoContent());
  }

  public ApiCloneWorkspaceResult getCloneWorkspaceResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, CLONE_WORKSPACE_RESULT_PATH_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(serializedResponse, ApiCloneWorkspaceResult.class);
  }

  public ApiWorkspaceDescription updateWorkspace(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String newUserFacingId,
      @Nullable String newDisplayName,
      @Nullable String newDescription)
      throws Exception {
    ApiUpdateWorkspaceRequestBody requestBody = new ApiUpdateWorkspaceRequestBody();
    if (newUserFacingId != null) {
      requestBody.userFacingId(newUserFacingId);
    }
    if (newDisplayName != null) {
      requestBody.displayName(newDisplayName);
    }
    if (newDescription != null) {
      requestBody.description(newDescription);
    }
    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(requestBody)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  public void updateWorkspaceProperties(
      AuthenticatedUserRequest userRequest, UUID workspaceId, List<ApiProperty> properties)
      throws Exception {
    getSerializedResponseForPost(
        userRequest,
        UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT,
        workspaceId,
        objectMapper.writeValueAsString(properties));
  }

  public void deleteWorkspaceProperties(
      AuthenticatedUserRequest userRequest, UUID workspaceId, List<String> propertyKeys)
      throws Exception {
    ApiPropertyKeys apiPropertyKeys = new ApiPropertyKeys();
    apiPropertyKeys.addAll(propertyKeys);
    mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(apiPropertyKeys)),
                userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

  public void deleteWorkspace(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                delete(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  // Delete Workspace variant when we don't know if workspaceId exists.
  public int deleteWorkspaceNoCheck(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(
                addAuth(
                    delete(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId)),
                    userRequest))
            .andReturn();
    return mvcResult.getResponse().getStatus();
  }

  public ApiWsmPolicyUpdateResult updatePolicies(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable List<ApiWsmPolicyInput> policiesToAdd,
      @Nullable List<ApiWsmPolicyInput> policiesToRemove)
      throws Exception {
    return updatePoliciesExpectStatus(
        userRequest, workspaceId, policiesToAdd, policiesToRemove, HttpStatus.SC_OK);
  }

  public ApiWsmPolicyUpdateResult updatePoliciesExpectStatus(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable List<ApiWsmPolicyInput> policiesToAdd,
      @Nullable List<ApiWsmPolicyInput> policiesToRemove,
      int httpStatus)
      throws Exception {

    ApiWsmPolicyUpdateRequest requestBody =
        new ApiWsmPolicyUpdateRequest().updateMode(ApiWsmPolicyUpdateMode.FAIL_ON_CONFLICT);
    if (policiesToAdd != null) {
      requestBody.addAttributes(new ApiWsmPolicyInputs().inputs(policiesToAdd));
    }
    if (policiesToRemove != null) {
      requestBody.removeAttributes(new ApiWsmPolicyInputs().inputs(policiesToRemove));
    }
    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(UPDATE_POLICIES_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(requestBody)),
                    userRequest))
            .andExpect(status().is(httpStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyUpdateResult.class);
  }

  public void deletePolicies(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    ApiWorkspaceDescription workspace = getWorkspace(userRequest, workspaceId);
    updatePolicies(
        userRequest,
        workspaceId,
        /*policiesToAdd=*/ null,
        /*policiesToRemove=*/ workspace.getPolicies().stream()
            .filter(
                p ->
                    // We cannot remove group policies but will remove all others.
                    !(p.getNamespace().equals(PolicyFixtures.NAMESPACE)
                        && p.getName().equals(PolicyFixtures.GROUP_CONSTRAINT)))
            .collect(Collectors.toList()));
  }

  public UUID createWorkspaceWithRegionConstraint(
      AuthenticatedUserRequest userRequest, String regionName) throws Exception {
    ApiWsmPolicyInputs regionPolicy =
        new ApiWsmPolicyInputs()
            .addInputsItem(
                new ApiWsmPolicyInput()
                    .namespace(PolicyFixtures.NAMESPACE)
                    .name(PolicyFixtures.REGION_CONSTRAINT)
                    .addAdditionalDataItem(
                        new ApiWsmPolicyPair().key(PolicyFixtures.REGION).value(regionName)));
    ApiCreatedWorkspace workspace = createWorkspaceWithPolicy(userRequest, regionPolicy);
    return workspace.getId();
  }

  public UUID createWorkspaceWithGroupConstraint(
      AuthenticatedUserRequest userRequest, String groupName) throws Exception {
    ApiWsmPolicyInputs groupPolicy =
        new ApiWsmPolicyInputs()
            .addInputsItem(
                new ApiWsmPolicyInput()
                    .namespace(PolicyFixtures.NAMESPACE)
                    .name(PolicyFixtures.GROUP_CONSTRAINT)
                    .addAdditionalDataItem(
                        new ApiWsmPolicyPair().key(PolicyFixtures.GROUP).value(groupName)));
    ApiCreatedWorkspace workspace = createWorkspaceWithPolicy(userRequest, groupPolicy);
    return workspace.getId();
  }

  public UUID createWorkspaceWithRegionConstraintAndCloudContext(
      AuthenticatedUserRequest userRequest, String regionName) throws Exception {
    UUID resultWorkspaceId = createWorkspaceWithRegionConstraint(userRequest, regionName);
    createGcpCloudContextAndWait(userRequest, resultWorkspaceId);

    return resultWorkspaceId;
  }

  public void assertWorkspace(
      ApiWorkspaceDescription actualWorkspace,
      String expectedUserFacingId,
      String expectedDisplayName,
      String expectedDescription,
      String expectedCreatedByEmail,
      String expectedLastUpdatedByEmail) {
    assertEquals(expectedUserFacingId, actualWorkspace.getUserFacingId());
    assertEquals(expectedDisplayName, actualWorkspace.getDisplayName());
    assertEquals(expectedDescription, actualWorkspace.getDescription());
    OffsetDateTime firstLastUpdatedDate = actualWorkspace.getLastUpdatedDate();
    assertNotNull(firstLastUpdatedDate);
    OffsetDateTime createdDate = actualWorkspace.getCreatedDate();
    assertNotNull(createdDate);
    assertTrue(firstLastUpdatedDate.isAfter(createdDate));
    assertEquals(expectedCreatedByEmail, actualWorkspace.getCreatedBy());
    assertEquals(expectedLastUpdatedByEmail, actualWorkspace.getLastUpdatedBy());
  }

  public ApiCreatedControlledGcpAiNotebookInstanceResult createAiNotebookInstance(
      AuthenticatedUserRequest userRequest, UUID workspaceId, @Nullable String location)
      throws Exception {
    return createAiNotebookInstanceAndWait(
        userRequest, workspaceId, /*instanceId=*/ null, location);
  }

  public ApiCreatedControlledGcpAiNotebookInstanceResult createAiNotebookInstanceAndWait(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String instanceId,
      @Nullable String location)
      throws Exception {
    return createAiNotebookInstanceAndExpect(
        userRequest, workspaceId, instanceId, location, StatusEnum.SUCCEEDED);
  }

  public ApiCreatedControlledGcpAiNotebookInstanceResult createAiNotebookInstanceAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String instanceId,
      @Nullable String location,
      StatusEnum jobStatus)
      throws Exception {
    ApiCreateControlledGcpAiNotebookInstanceRequestBody request =
        new ApiCreateControlledGcpAiNotebookInstanceRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("ai-notebook")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .aiNotebookInstance(
                ControlledGcpResourceFixtures.defaultNotebookCreationParameters()
                    .location(location)
                    .instanceId(
                        Optional.ofNullable(instanceId)
                            .orElse(TestUtils.appendRandomNumber("instance-id"))));

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_GCP_AI_NOTEBOOKS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        objectMapper.readValue(
            serializedResponse, ApiCreatedControlledGcpAiNotebookInstanceResult.class);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(5);
      result = getAiNotebookInstanceResult(userRequest, workspaceId, jobId);
    }
    assertEquals(jobStatus, result.getJobReport().getStatus());

    return result;
  }

  private ApiCreatedControlledGcpAiNotebookInstanceResult getAiNotebookInstanceResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, CONTROLLED_GCP_AI_NOTEBOOKS_V1_RESULT_PATH_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(
        serializedResponse, ApiCreatedControlledGcpAiNotebookInstanceResult.class);
  }

  public ApiCreatedControlledGcpGceInstanceResult createGceInstance(
      AuthenticatedUserRequest userRequest, UUID workspaceId, @Nullable String zone)
      throws Exception {
    return createGceInstanceAndWait(userRequest, workspaceId, /*instanceId=*/ null, zone);
  }

  public ApiCreatedControlledGcpGceInstanceResult createGceInstanceAndWait(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String instanceId,
      @Nullable String zone)
      throws Exception {
    return createGceInstanceAndExpect(
        userRequest, workspaceId, instanceId, zone, StatusEnum.SUCCEEDED);
  }

  public ApiCreatedControlledGcpGceInstanceResult createGceInstanceAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String instanceId,
      @Nullable String zone,
      StatusEnum jobStatus)
      throws Exception {
    ApiCreateControlledGcpGceInstanceRequestBody request =
        new ApiCreateControlledGcpGceInstanceRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("gce-instance")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .gceInstance(
                ControlledGcpResourceFixtures.defaultGceInstanceCreationParameters()
                    .zone(zone)
                    .instanceId(
                        Optional.ofNullable(instanceId)
                            .orElse(TestUtils.appendRandomNumber("instance-id"))));

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_GCP_GCE_INSTANCES_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    ApiCreatedControlledGcpGceInstanceResult result =
        objectMapper.readValue(serializedResponse, ApiCreatedControlledGcpGceInstanceResult.class);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getGceInstanceResult(userRequest, workspaceId, jobId);
    }
    assertEquals(jobStatus, result.getJobReport().getStatus());

    return result;
  }

  private ApiCreatedControlledGcpGceInstanceResult getGceInstanceResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, CONTROLLED_GCP_GCE_INSTANCES_V1_RESULT_PATH_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(
        serializedResponse, ApiCreatedControlledGcpGceInstanceResult.class);
  }

  public ApiCreatedControlledGcpBigQueryDataset createControlledBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    return createControlledBqDataset(
        userRequest,
        workspaceId,
        /*resourceName=*/ TestUtils.appendRandomNumber("resource-name"),
        /*datasetName=*/ TestUtils.appendRandomNumber("dataset-name"),
        /*location=*/ null,
        /*defaultTableLifetime=*/ null,
        /*defaultPartitionTableLifetime=*/ null);
  }

  public ApiCreatedControlledGcpBigQueryDataset createControlledBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String datasetName,
      @Nullable String location,
      @Nullable Long defaultTableLifetime,
      @Nullable Long defaultPartitionLifetime)
      throws Exception {
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetName);
    if (location != null) {
      creationParameters.setLocation(location);
    }
    if (defaultTableLifetime != null) {
      creationParameters.setDefaultTableLifetime(defaultTableLifetime);
    }
    if (defaultPartitionLifetime != null) {
      creationParameters.defaultPartitionLifetime(defaultPartitionLifetime);
    }
    ApiCreateControlledGcpBigQueryDatasetRequestBody request =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .name(resourceName))
            .dataset(creationParameters);

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledGcpBigQueryDataset.class);
  }

  public void deleteBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      StewardshipType stewardshipType)
      throws Exception {
    deleteResource(
        userRequest,
        workspaceId,
        resourceId,
        StewardshipType.CONTROLLED.equals(stewardshipType)
            ? CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT
            : REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);
  }

  public void deleteBqDataTable(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT);
  }

  public void deleteReferencedGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    deleteResource(userRequest, workspaceId, resourceId, REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT);
  }

  public void deleteGcsObject(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    deleteResource(userRequest, workspaceId, resourceId, REFERENCED_GCP_GCS_OBJECT_V1_PATH_FORMAT);
  }

  public void deleteDataRepoSnapshot(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_DATA_REPO_SNAPSHOT_V1_PATH_FORMAT);
  }

  public void deleteGitRepo(AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId)
      throws Exception {
    deleteResource(userRequest, workspaceId, resourceId, REFERENCED_GIT_REPO_V1_PATH_FORMAT);
  }

  private void deleteResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId, String path)
      throws Exception {
    mockMvc
        .perform(addAuth(delete(String.format(path, workspaceId, resourceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

  public ApiGcpBigQueryDatasetResource getControlledBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    return getBqDataset(
        userRequest, workspaceId, resourceId, CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);
  }

  public ApiGcpBigQueryDatasetResource updateControlledBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      ApiCloningInstructionsEnum newCloningInstruction)
      throws Exception {
    ApiUpdateControlledGcpBigQueryDatasetRequestBody requestBody =
        new ApiUpdateControlledGcpBigQueryDatasetRequestBody()
            .name(newName)
            .description(newDescription)
            .updateParameters(
                new ApiGcpBigQueryDatasetUpdateParameters()
                    .cloningInstructions(newCloningInstruction));
    return updateResource(
        ApiGcpBigQueryDatasetResource.class,
        CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT,
        workspaceId,
        resourceId,
        objectMapper.writeValueAsString(requestBody),
        userRequest,
        HttpStatus.SC_OK);
  }

  private ApiGcpBigQueryDatasetResource getBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId, String path)
      throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(userRequest, path, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
  }

  public ApiGcpBigQueryDatasetResource cloneControlledBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName)
      throws Exception {
    return cloneControlledBqDataset(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        destDatasetName,
        /*location=*/ null,
        /*defaultTableLifetime=*/ null,
        /*defaultPartitionLifetime=*/ null);
  }

  /** Call cloneBigQueryDataset() and wait for flight to finish. */
  public ApiGcpBigQueryDatasetResource cloneControlledBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      @Nullable String destLocation,
      @Nullable Long defaultTableLifetime,
      @Nullable Long defaultPartitionLifetime)
      throws Exception {
    ApiCloneControlledGcpBigQueryDatasetResult result =
        cloneControlledBqDatasetAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destDatasetName,
            destLocation,
            defaultTableLifetime,
            defaultPartitionLifetime,
            // clone_copyNothing sometimes returns SC_OK, even for the initial call. So accept both
            // to avoid flakes.
            JOB_SUCCESS_CODES,
            /*shouldUndo=*/ false);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getCloneControlledBqDatasetResult(userRequest, sourceWorkspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled BQ dataset clone of resource %s completed. ".formatted(sourceResourceId));
    return result.getDataset().getDataset();
  }

  /** Call cloneBigQueryDataset(), wait for flight to finish, return JobError. */
  public ApiErrorReport cloneControlledBqDataset_jobError(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      int expectedCode)
      throws Exception {
    ApiCloneControlledGcpBigQueryDatasetResult result =
        cloneControlledBqDatasetAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destDatasetName,
            /*destLocation=*/ null,
            /*defaultTableLifetime=*/ null,
            /*defaultPartitionLifetime=*/ null,
            List.of(HttpStatus.SC_ACCEPTED),
            /*shouldUndo=*/ false);
    return cloneControlledBqDataset_waitForJobError(
        userRequest, sourceWorkspaceId, result.getJobReport().getId(), expectedCode);
  }

  public void cloneControlledBqDataset_undo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      String destResourceName)
      throws Exception {
    ApiCloneControlledGcpBigQueryDatasetResult result =
        cloneControlledBqDatasetAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            /*destDatasetName=*/ null,
            /*destLocation=*/ null,
            /*defaultTableLifetime=*/ null,
            /*defaultPartitionLifetime=*/ null,
            List.of(HttpStatus.SC_ACCEPTED),
            /*shouldUndo=*/ true);
    cloneControlledBqDataset_waitForJobError(
        userRequest,
        sourceWorkspaceId,
        result.getJobReport().getId(),
        HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  /** Call cloneBigQueryDataset() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpBigQueryDatasetResult cloneControlledBqDatasetAsync(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      @Nullable String destLocation,
      @Nullable Long defaultTableLifetime,
      @Nullable Long defaultPartitionLifetime,
      List<Integer> expectedCodes,
      boolean shouldUndo)
      throws Exception {
    // Retry to ensure steps are idempotent
    Map<String, StepStatus> retryableStepsMap = new HashMap<>();
    List<Class<? extends Step>> retryableSteps =
        ImmutableList.of(
            CheckControlledResourceAuthStep.class,
            SetReferencedDestinationBigQueryDatasetInWorkingMapStep.class,
            CreateReferenceMetadataStep.class,
            SetReferencedDestinationBigQueryDatasetResponseStep.class,
            CreateTableCopyJobsStep.class,
            CompleteTableCopyJobsStep.class);
    retryableSteps.forEach(
        step -> retryableStepsMap.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(retryableStepsMap)
            .lastStepFailure(shouldUndo)
            .build());

    ApiCloneControlledGcpBigQueryDatasetRequest request =
        new ApiCloneControlledGcpBigQueryDatasetRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .location(destLocation)
            .defaultTableLifetime(defaultTableLifetime)
            .defaultPartitionLifetime(defaultPartitionLifetime)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    if (!StringUtils.isEmpty(destResourceName)) {
      request.name(destResourceName);
    }
    if (!StringUtils.isEmpty(destDatasetName)) {
      request.destinationDatasetName(destDatasetName);
    }

    MockHttpServletResponse response =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CLONE_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(getExpectedCodesMatcher(expectedCodes)))
            .andReturn()
            .getResponse();

    // Disable the debug info post flight
    jobService.setFlightDebugInfoForTest(null);

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper.readValue(
        serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class);
  }

  private ApiErrorReport cloneControlledBqDataset_waitForJobError(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId, int expectedCode)
      throws Exception {
    // While job is running, cloneBigQueryDataset returns ApiCloneControlledGcpBigQueryDatasetResult
    // After job fails, cloneBigQueryData returns ApiCloneControlledGcpBigQueryDatasetResult OR
    // ApiErrorReport.
    ApiCloneControlledGcpBigQueryDatasetResult result =
        getCloneControlledBqDatasetResult(userRequest, workspaceId, jobId);
    ApiErrorReport errorReport;
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 3000);
      String serializedResponse =
          getSerializedResponseForGetJobResult_error(
              userRequest,
              CLONE_RESULT_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT,
              workspaceId,
              jobId);
      try {
        result =
            objectMapper.readValue(
                serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class);
      } catch (UnrecognizedPropertyException e) {
        errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
        assertEquals(expectedCode, errorReport.getStatusCode());
        return errorReport;
      }
    }
    // Job failed and cloneBigQueryData returned ApiCloneControlledGcpBigQueryDatasetResult
    assertEquals(StatusEnum.FAILED, result.getJobReport().getStatus());
    return result.getErrorReport();
  }

  private ApiCloneControlledGcpBigQueryDatasetResult getCloneControlledBqDatasetResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, CLONE_RESULT_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(
        serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class);
  }

  public ApiCreatedControlledGcpGcsBucket createControlledGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String bucketName,
      String location,
      ApiGcpGcsBucketDefaultStorageClass storageClass,
      ApiGcpGcsBucketLifecycle lifecycle)
      throws Exception {
    ApiCreateControlledGcpGcsBucketRequestBody request =
        new ApiCreateControlledGcpGcsBucketRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .name(resourceName))
            .gcsBucket(
                new ApiGcpGcsBucketCreationParameters()
                    .name(bucketName)
                    .location(location)
                    .defaultStorageClass(storageClass)
                    .lifecycle(lifecycle));

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_GCP_GCS_BUCKETS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledGcpGcsBucket.class);
  }

  public ApiGcpGcsBucketResource getControlledGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedGetResponse =
        getSerializedResponseForGet(
            userRequest, CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedGetResponse, ApiGcpGcsBucketResource.class);
  }

  public ApiGcpGcsBucketResource updateControlledGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      ApiCloningInstructionsEnum newCloningInstruction)
      throws Exception {
    ApiUpdateControlledGcpGcsBucketRequestBody requestBody =
        new ApiUpdateControlledGcpGcsBucketRequestBody()
            .name(newName)
            .description(newDescription)
            .updateParameters(
                new ApiGcpGcsBucketUpdateParameters().cloningInstructions(newCloningInstruction));
    return updateResource(
        ApiGcpGcsBucketResource.class,
        CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT,
        workspaceId,
        resourceId,
        objectMapper.writeValueAsString(requestBody),
        userRequest,
        HttpStatus.SC_OK);
  }

  /** Call cloneGcsBucket() and wait for flight to finish. */
  public ApiCreatedControlledGcpGcsBucket cloneControlledGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destBucketName,
      @Nullable String destLocation)
      throws Exception {
    ApiCloneControlledGcpGcsBucketResult result =
        cloneControlledGcsBucketAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destBucketName,
            destLocation,
            // clone_copyNothing sometimes returns SC_OK, even for the initial call. So accept both
            // to avoid flakes.
            JOB_SUCCESS_CODES,
            /*shouldUndo=*/ false);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getCloneControlledGcsBucketResult(userRequest, sourceWorkspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled GCS bucket clone of resource %s completed. ".formatted(sourceResourceId));
    return result.getBucket().getBucket();
  }

  /** Call cloneGcsBucket(), wait for flight to finish, return JobError. */
  public ApiErrorReport cloneControlledGcsBucket_jobError(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destBucketName,
      int expectedCode)
      throws Exception {
    ApiCloneControlledGcpGcsBucketResult result =
        cloneControlledGcsBucketAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            /*destResourceName=*/ null,
            destBucketName,
            /*destLocation=*/ null,
            // clone_copyNothing sometimes returns SC_OK, even for the initial call. So accept both
            // to avoid flakes.
            JOB_SUCCESS_CODES,
            /*shouldUndo=*/ false);
    return cloneControlledGcsBucket_waitForJobError(
        userRequest, sourceWorkspaceId, result.getJobReport().getId(), expectedCode);
  }

  public void cloneControlledGcsBucket_undo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      String destResourceName)
      throws Exception {
    ApiCloneControlledGcpGcsBucketResult result =
        cloneControlledGcsBucketAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            /*destBucketName=*/ null,
            /*destLocation=*/ null,
            // clone_copyNothing sometimes returns SC_OK, even for the initial call. So accept both
            // to avoid flakes.
            JOB_SUCCESS_CODES,
            /*shouldUndo=*/ true);
    cloneControlledBqDataset_waitForJobError(
        userRequest,
        sourceWorkspaceId,
        result.getJobReport().getId(),
        HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  /** Call cloneGcsBucket() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpGcsBucketResult cloneControlledGcsBucketAsync(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destBucketName,
      @Nullable String destLocation,
      List<Integer> expectedCodes,
      boolean shouldUndo)
      throws Exception {
    // Retry to ensure steps are idempotent
    Map<String, StepStatus> retryableStepsMap = new HashMap<>();
    List<Class> retryableSteps =
        ImmutableList.of(
            CheckControlledResourceAuthStep.class,
            RetrieveControlledResourceMetadataStep.class,
            RetrieveGcsBucketCloudAttributesStep.class,
            SetReferencedDestinationGcsBucketInWorkingMapStep.class,
            CreateReferenceMetadataStep.class,
            SetReferencedDestinationGcsBucketResponseStep.class,
            SetBucketRolesStep.class,
            TransferGcsBucketToGcsBucketStep.class,
            CompleteTransferOperationStep.class,
            // TODO(PF-2271): Uncomment after PF-2271 is fixed
            // DeleteStorageTransferServiceJobStep.class,
            RemoveBucketRolesStep.class);
    retryableSteps.forEach(
        step -> retryableStepsMap.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(retryableStepsMap)
            .lastStepFailure(shouldUndo)
            .build());

    ApiCloneControlledGcpGcsBucketRequest request =
        new ApiCloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .name(TestUtils.appendRandomNumber(DEST_BUCKET_RESOURCE_NAME))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    if (!StringUtils.isEmpty(destResourceName)) {
      request.name(destResourceName);
    }
    if (!StringUtils.isEmpty(destBucketName)) {
      request.bucketName(destBucketName);
    }
    if (!StringUtils.isEmpty(destLocation)) {
      request.location(destLocation);
    }
    MockHttpServletResponse response =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CLONE_CONTROLLED_GCP_GCS_BUCKET_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(getExpectedCodesMatcher(expectedCodes)))
            .andReturn()
            .getResponse();

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
  }

  private ApiErrorReport cloneControlledGcsBucket_waitForJobError(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId, int expectedCode)
      throws Exception {
    // While job is running, cloneGcsBucket returns ApiCloneControlledGcpGcsBucketResult
    // After job fails, cloneGcsBucket returns ApiCloneControlledGcpGcsBucketResult OR
    // ApiErrorReport.
    ApiCloneControlledGcpGcsBucketResult result =
        getCloneControlledGcsBucketResult(userRequest, workspaceId, jobId);
    ApiErrorReport errorReport;
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 3000);
      String serializedResponse =
          getSerializedResponseForGetJobResult_error(
              userRequest, CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT, workspaceId, jobId);
      try {
        result =
            objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
      } catch (UnrecognizedPropertyException e) {
        errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
        assertEquals(expectedCode, errorReport.getStatusCode());
        return errorReport;
      }
    }
    // Job failed and cloneBigQueryData returned ApiCloneControlledGcpBigQueryDatasetResult
    assertEquals(StatusEnum.FAILED, result.getJobReport().getStatus());
    return result.getErrorReport();
  }

  private ApiCloneControlledGcpGcsBucketResult getCloneControlledGcsBucketResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGetJobResult(
            userRequest, CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT, workspaceId, jobId);
    return objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
  }

  public ApiFlexibleResource getFlexibleResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    var serializedResponse =
        getSerializedResponseForGet(
            userRequest, CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiFlexibleResource.class);
  }

  public void getFlexibleResourceExpect(UUID workspaceId, UUID resourceId, int httpStatus)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                get(CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT.formatted(workspaceId, resourceId)),
                USER_REQUEST))
        .andExpect(status().is(httpStatus));
  }

  public ApiCreatedControlledFlexibleResource createFlexibleResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    return createFlexibleResource(
        userRequest,
        workspaceId,
        /*resourceName=*/ TestUtils.appendRandomNumber("resource-name"),
        /*typeNamespace=*/ "terra",
        /*type*/ "default-fake-flexible-type",
        /*data*/ null);
  }

  public ApiCreateControlledFlexibleResourceRequestBody createFlexibleResourceRequestBody(
      String resourceName, String typeNamespace, String type, @Nullable byte[] data) {
    ApiControlledFlexibleResourceCreationParameters creationParameters =
        new ApiControlledFlexibleResourceCreationParameters()
            .typeNamespace(typeNamespace)
            .type(type);
    if (data != null) {
      creationParameters.setData(data);
    }

    return new ApiCreateControlledFlexibleResourceRequestBody()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi().name(resourceName))
        .flexibleResource(creationParameters);
  }

  public ApiCreatedControlledFlexibleResource createFlexibleResource(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String typeNamespace,
      String type,
      @Nullable byte[] data)
      throws Exception {
    ApiCreateControlledFlexibleResourceRequestBody request =
        createFlexibleResourceRequestBody(resourceName, typeNamespace, type, data);

    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            CONTROLLED_FLEXIBLE_RESOURCES_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledFlexibleResource.class);
  }

  public void deleteFlexibleResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    deleteResource(
        userRequest, workspaceId, resourceId, CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT);
  }

  public ApiFlexibleResource updateFlexibleResource(
      UUID workspaceId,
      UUID resourceId,
      @Nullable String newResourceName,
      @Nullable String newDescription,
      @Nullable byte[] newData,
      @Nullable ApiCloningInstructionsEnum newCloningInstructions)
      throws Exception {
    String request =
        objectMapper.writeValueAsString(
            getUpdateFlexibleResourceRequestBody(
                newResourceName, newDescription, newData, newCloningInstructions));

    return updateResource(
        ApiFlexibleResource.class,
        CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT,
        workspaceId,
        resourceId,
        request,
        USER_REQUEST,
        HttpStatus.SC_OK);
  }

  public void updateFlexibleResourceExpect(
      UUID workspaceId,
      UUID resourceId,
      @Nullable String newResourceName,
      @Nullable String newDescription,
      @Nullable byte[] newData,
      @Nullable ApiCloningInstructionsEnum newCloningInstructions,
      int code)
      throws Exception {
    String request =
        objectMapper.writeValueAsString(
            getUpdateFlexibleResourceRequestBody(
                newResourceName, newDescription, newData, newCloningInstructions));

    updateResource(
        ApiFlexibleResource.class,
        CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT,
        workspaceId,
        resourceId,
        request,
        USER_REQUEST,
        code);
  }

  private ApiUpdateControlledFlexibleResourceRequestBody getUpdateFlexibleResourceRequestBody(
      @Nullable String newResourceName,
      @Nullable String newDescription,
      @Nullable byte[] newData,
      @Nullable ApiCloningInstructionsEnum newCloningInstructions) {
    return new ApiUpdateControlledFlexibleResourceRequestBody()
        .description(newDescription)
        .name(newResourceName)
        .updateParameters(
            new ApiFlexibleResourceUpdateParameters()
                .data(newData)
                .cloningInstructions(newCloningInstructions));
  }

  public ApiCloneControlledFlexibleResourceResult cloneFlexResourceHelper(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDescription,
      List<Integer> expectedCodes,
      boolean shouldUndo)
      throws Exception {
    // Retry to ensure steps are idempotent
    Map<String, StepStatus> retryableStepsMap = new HashMap<>();
    List<Class<? extends Step>> retryableSteps =
        ImmutableList.of(CheckControlledResourceAuthStep.class);
    retryableSteps.forEach(
        step -> retryableStepsMap.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(retryableStepsMap)
            .lastStepFailure(shouldUndo)
            .build());

    ApiCloneControlledFlexibleResourceRequest request =
        new ApiCloneControlledFlexibleResourceRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .description(destDescription);

    if (!StringUtils.isEmpty(destResourceName)) {
      request.name(destResourceName);
    }

    MockHttpServletResponse response =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CLONE_CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(getExpectedCodesMatcher(expectedCodes)))
            .andReturn()
            .getResponse();

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper.readValue(
        serializedResponse, ApiCloneControlledFlexibleResourceResult.class);
  }

  /** Call cloneFlexResource() and wait for the flight to finish. */
  public ApiFlexibleResource cloneFlexResource(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDescription)
      throws Exception {
    ApiCloneControlledFlexibleResourceResult result =
        cloneFlexResourceHelper(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destDescription,
            // clone_copyNothing sometimes returns SC_OK, even for the initial call. So accept both
            // to avoid flakes.
            JOB_SUCCESS_CODES,
            /*shouldUndo=*/ false);
    logger.info("Controlled flex clone of resource %s completed.".formatted(sourceResourceId));
    return result.getResource();
  }

  public void cloneFlex_undo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDescription)
      throws Exception {
    cloneFlexResourceHelper(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        destDescription,
        List.of(HttpStatus.SC_INTERNAL_SERVER_ERROR),
        /*shouldUndo=*/ true);
  }

  public void cloneFlex_forbidden(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDescription)
      throws Exception {
    cloneFlexResourceHelper(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        destDescription,
        List.of(HttpStatus.SC_FORBIDDEN),
        /*shouldUndo=*/ false);
  }

  public ApiDataRepoSnapshotResource createReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      String resourceName,
      String instanceName,
      String snapshot)
      throws Exception {
    ApiDataRepoSnapshotAttributes creationParameters =
        new ApiDataRepoSnapshotAttributes().instanceName(instanceName).snapshot(snapshot);
    ApiCreateDataRepoSnapshotReferenceRequestBody request =
        new ApiCreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName)
                    .cloningInstructions(cloningInstructions))
            .snapshot(creationParameters);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  public ApiDataRepoSnapshotResource getReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(
            userRequest, REFERENCED_DATA_REPO_SNAPSHOT_V1_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  public ApiDataRepoSnapshotResource updateReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      String newSnapshot,
      String newInstanceName,
      ApiCloningInstructionsEnum newCloningInstruction)
      throws Exception {
    ApiUpdateDataRepoSnapshotReferenceRequestBody requestBody =
        new ApiUpdateDataRepoSnapshotReferenceRequestBody()
            .name(newName)
            .description(newDescription)
            .cloningInstructions(newCloningInstruction)
            .snapshot(newSnapshot)
            .instanceName(newInstanceName);
    var serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_DATA_REPO_SNAPSHOT_V1_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  public ApiDataRepoSnapshotResource cloneReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedDataRepoSnapshot(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiDataRepoSnapshotResource cloneReferencedDataRepoSnapshot(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        cloneReferencedResource(
            userRequest,
            CLONE_REFERENCED_DATA_REPO_SNAPSHOT_V1_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    // If an exception was thrown, deserialization won't work, so don't attempt it.
    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpDataRepoSnapshotResourceResult.class)
        .getResource();
  }

  public ApiGcpBigQueryDatasetResource createReferencedBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String projectId,
      String datasetName)
      throws Exception {
    ApiGcpBigQueryDatasetAttributes creationParameters =
        new ApiGcpBigQueryDatasetAttributes().projectId(projectId).datasetId(datasetName);
    ApiCreateGcpBigQueryDatasetReferenceRequestBody request =
        new ApiCreateGcpBigQueryDatasetReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName))
            .dataset(creationParameters);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            REFERENCED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
  }

  public ApiGcpBigQueryDatasetResource getReferencedBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    return getBqDataset(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);
  }

  public ApiGcpBigQueryDatasetResource updateReferencedBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      ApiCloningInstructionsEnum newCloningInstruction,
      String newBqDataset)
      throws Exception {
    ApiUpdateBigQueryDatasetReferenceRequestBody requestBody =
        new ApiUpdateBigQueryDatasetReferenceRequestBody()
            .name(newName)
            .description(newDescription)
            .cloningInstructions(newCloningInstruction)
            .datasetId(newBqDataset);
    var serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
  }

  public ApiGcpBigQueryDatasetResource cloneReferencedBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedBqDataset(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpBigQueryDatasetResource cloneReferencedBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        cloneReferencedResource(
            userRequest,
            CLONE_REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpBigQueryDatasetResourceResult.class)
        .getResource();
  }

  public ApiGcpBigQueryDataTableResource createReferencedBqTable(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String projectId,
      String datasetName,
      String tableId)
      throws Exception {
    ApiGcpBigQueryDataTableAttributes creationParameters =
        new ApiGcpBigQueryDataTableAttributes()
            .projectId(projectId)
            .datasetId(datasetName)
            .dataTableId(tableId);
    ApiCreateGcpBigQueryDataTableReferenceRequestBody request =
        new ApiCreateGcpBigQueryDataTableReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName))
            .dataTable(creationParameters);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            REFERENCED_GCP_BIG_QUERY_DATA_TABLES_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  public ApiGcpBigQueryDataTableResource getReferencedBqTable(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(
            userRequest,
            REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT,
            workspaceId,
            resourceId);
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  public ApiGcpBigQueryDataTableResource updateReferencedBqTable(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      ApiCloningInstructionsEnum newCloningInstruction,
      String newProjectId,
      String newDataset,
      String newTable)
      throws Exception {
    ApiUpdateBigQueryDataTableReferenceRequestBody requestBody =
        new ApiUpdateBigQueryDataTableReferenceRequestBody()
            .name(newName)
            .description(newDescription)
            .cloningInstructions(newCloningInstruction)
            .projectId(newProjectId)
            .datasetId(newDataset)
            .dataTableId(newTable);
    var serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            String.format(
                REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  public ApiGcpBigQueryDataTableResource cloneReferencedBqTable(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedBqTable(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpBigQueryDataTableResource cloneReferencedBqTable(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        cloneReferencedResource(
            userRequest,
            CLONE_REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpBigQueryDataTableResourceResult.class)
        .getResource();
  }

  public ApiGcpGcsBucketResource createReferencedGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String bucketName)
      throws Exception {
    ApiGcpGcsBucketAttributes creationParameters =
        new ApiGcpGcsBucketAttributes().bucketName(bucketName);
    ApiCreateGcpGcsBucketReferenceRequestBody request =
        new ApiCreateGcpGcsBucketReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName))
            .bucket(creationParameters);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  public ApiGcpGcsBucketResource getReferencedGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(
            userRequest, REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  public ApiGcpGcsBucketResource updateReferencedGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      String newBucketName,
      ApiCloningInstructionsEnum newCloneInstruction)
      throws Exception {
    ApiUpdateGcsBucketReferenceRequestBody requestBody =
        new ApiUpdateGcsBucketReferenceRequestBody()
            .name(newName)
            .description(newDescription)
            .bucketName(newBucketName)
            .cloningInstructions(newCloneInstruction);
    var serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  public ApiGcpGcsBucketResource cloneReferencedGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedGcsBucket(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpGcsBucketResource cloneReferencedGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        cloneReferencedResource(
            userRequest,
            CLONE_REFERENCED_GCP_GCS_BUCKET_V1_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpGcsBucketResourceResult.class)
        .getResource();
  }

  public ApiGcpGcsObjectResource createReferencedGcsObject(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String bucketName,
      String fileName)
      throws Exception {
    ApiGcpGcsObjectAttributes creationParameters =
        new ApiGcpGcsObjectAttributes().bucketName(bucketName).fileName(fileName);
    ApiCreateGcpGcsObjectReferenceRequestBody request =
        new ApiCreateGcpGcsObjectReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName))
            .file(creationParameters);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpGcsObjectResource.class);
  }

  public ApiGcpGcsObjectResource getReferencedGcsObject(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(
            userRequest, REFERENCED_GCP_GCS_OBJECT_V1_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGcpGcsObjectResource.class);
  }

  public ApiGcpGcsObjectResource updateReferencedGcsObject(
      UUID workspaceId,
      UUID resourceId,
      String newName,
      String newDescription,
      String newBucketName,
      String newObjectName,
      ApiCloningInstructionsEnum newCloningInstruction,
      AuthenticatedUserRequest userRequest)
      throws Exception {
    ApiUpdateGcsBucketObjectReferenceRequestBody updateRequest =
        new ApiUpdateGcsBucketObjectReferenceRequestBody();
    updateRequest
        .name(newName)
        .description(newDescription)
        .cloningInstructions(newCloningInstruction)
        .bucketName(newBucketName)
        .objectName(newObjectName);

    var serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_GCS_OBJECT_V1_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(updateRequest));
    return objectMapper.readValue(serializedResponse, ApiGcpGcsObjectResource.class);
  }

  public ApiGcpGcsObjectResource cloneReferencedGcsObject(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedGcsObject(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpGcsObjectResource cloneReferencedGcsObject(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        cloneReferencedResource(
            userRequest,
            CLONE_REFERENCED_GCP_GCS_OBJECT_V1_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpGcsObjectResourceResult.class)
        .getResource();
  }

  public ApiGitRepoResource createReferencedGitRepo(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String resourceName,
      String gitRepoUrl)
      throws Exception {
    ApiGitRepoAttributes creationParameters = new ApiGitRepoAttributes().gitRepoUrl(gitRepoUrl);
    ApiCreateGitRepoReferenceRequestBody request =
        new ApiCreateGitRepoReferenceRequestBody()
            .metadata(
                ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi()
                    .name(resourceName))
            .gitrepo(creationParameters);
    String serializedResponse =
        getSerializedResponseForPost(
            userRequest,
            REFERENCED_GIT_REPOS_V1_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGitRepoResource.class);
  }

  public ApiGitRepoResource getReferencedGitRepo(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(
            userRequest, REFERENCED_GIT_REPO_V1_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGitRepoResource.class);
  }

  public ApiGitRepoResource updateReferencedGitRepo(
      UUID workspaceId,
      UUID resourceId,
      String newDisplayName,
      String newDescription,
      String newGitRepoUrl,
      ApiCloningInstructionsEnum cloningInstructionsEnum,
      AuthenticatedUserRequest userRequest)
      throws Exception {
    ApiUpdateGitRepoReferenceRequestBody requestBody = new ApiUpdateGitRepoReferenceRequestBody();
    if (newDisplayName != null) {
      requestBody.name(newDisplayName);
    }
    if (newDescription != null) {
      requestBody.description(newDescription);
    }
    if (newGitRepoUrl != null) {
      requestBody.gitRepoUrl(newGitRepoUrl);
    }
    if (cloningInstructionsEnum != null) {
      requestBody.cloningInstructions(cloningInstructionsEnum);
    }
    return updateResource(
        ApiGitRepoResource.class,
        REFERENCED_GIT_REPO_V1_PATH_FORMAT,
        workspaceId,
        resourceId,
        objectMapper.writeValueAsString(requestBody),
        userRequest,
        HttpStatus.SC_OK);
  }

  /**
   * Expect a code when updating, and return the serialized API resource if expected code is
   * successful.
   */
  public <T> T updateResource(
      Class<T> classType,
      String pathFormat,
      UUID workspaceId,
      UUID resourceId,
      String requestBody,
      AuthenticatedUserRequest userRequest,
      int code)
      throws Exception {
    ResultActions result =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(pathFormat, workspaceId, resourceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestBody),
                    userRequest))
            .andExpect(status().is(code));

    // If not successful then don't serialize the response.
    if (code >= 300) {
      return null;
    }

    String serializedResponse = result.andReturn().getResponse().getContentAsString();

    return objectMapper.readValue(serializedResponse, classType);
  }

  public ApiGitRepoResource cloneReferencedGitRepo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedGitRepo(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGitRepoResource cloneReferencedGitRepo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        cloneReferencedResource(
            userRequest,
            CLONE_REFERENCED_GIT_REPO_V1_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGitRepoResourceResult.class)
        .getResource();
  }

  private MockHttpServletResponse cloneReferencedResource(
      AuthenticatedUserRequest userRequest,
      String path,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    ApiCloneReferencedResourceRequestBody request =
        new ApiCloneReferencedResourceRequestBody()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions);
    if (!StringUtils.isEmpty(destResourceName)) {
      request.name(destResourceName);
    }

    return mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(path.formatted(sourceWorkspaceId, sourceResourceId))
                        .content(objectMapper.writeValueAsString(request)),
                    userRequest)))
        .andExpect(status().is(expectedCode))
        .andReturn()
        .getResponse();
  }

  public List<ApiResourceDescription> enumerateResources(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    String serializedResponse =
        getSerializedResponseForGet(userRequest, RESOURCES_PATH_FORMAT, workspaceId);
    return objectMapper.readValue(serializedResponse, ApiResourceList.class).getResources();
  }

  public static void assertControlledResourceMetadata(
      ApiControlledResourceMetadata actualMetadata,
      ApiAccessScope expectedAccessScope,
      ApiManagedBy expectedManagedByType,
      ApiPrivateResourceUser expectedPrivateResourceUser,
      ApiPrivateResourceState expectedPrivateResourceState,
      @Nullable String region) {
    assertEquals(expectedAccessScope, actualMetadata.getAccessScope());
    assertEquals(expectedManagedByType, actualMetadata.getManagedBy());
    assertEquals(expectedPrivateResourceUser, actualMetadata.getPrivateResourceUser());
    assertEquals(expectedPrivateResourceState, actualMetadata.getPrivateResourceState());
    if (region != null) {
      assertEquals(
          region.toLowerCase(Locale.ROOT), actualMetadata.getRegion().toLowerCase(Locale.ROOT));
    }
  }

  public static void assertResourceMetadata(
      ApiResourceMetadata actualMetadata,
      ApiCloudPlatform expectedCloudPlatform,
      ApiResourceType expectedResourceType,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      ApiResourceLineage expectedResourceLineage,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertEquals(expectedWorkspaceId, actualMetadata.getWorkspaceId());
    assertEquals(expectedResourceName, actualMetadata.getName());
    assertEquals(expectedResourceDescription, actualMetadata.getDescription());
    assertEquals(expectedResourceType, actualMetadata.getResourceType());
    assertEquals(expectedStewardshipType, actualMetadata.getStewardshipType());
    assertEquals(expectedCloudPlatform, actualMetadata.getCloudPlatform());
    assertEquals(expectedCloningInstructions, actualMetadata.getCloningInstructions());
    assertEquals(expectedResourceLineage, actualMetadata.getResourceLineage());
    assertEquals(expectedLastUpdatedBy, actualMetadata.getLastUpdatedBy());
    assertNotNull(actualMetadata.getLastUpdatedDate());
    assertEquals(expectedCreatedBy, actualMetadata.getCreatedBy());
    assertNotNull(actualMetadata.getCreatedDate());
    // last updated date must be equals or after created date.
    assertFalse(actualMetadata.getLastUpdatedDate().isBefore(actualMetadata.getCreatedDate()));

    assertEquals(
        PropertiesUtils.convertMapToApiProperties(DEFAULT_RESOURCE_PROPERTIES),
        actualMetadata.getProperties());
  }

  public void assertClonedResourceMetadata(
      ApiResourceMetadata actualMetadata,
      ApiCloudPlatform expectedCloudPlatform,
      ApiResourceType expectedResourceType,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      String expectedCreatedBy,
      StewardshipType sourceResourceStewardshipType,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    ApiResourceLineage expectedResourceLineage = new ApiResourceLineage();
    expectedResourceLineage.add(
        new ApiResourceLineageEntry()
            .sourceWorkspaceId(sourceWorkspaceId)
            .sourceResourceId(sourceResourceId));

    UserStatusInfo userStatusInfo = samService.getUserStatusInfo(userRequest);
    String expectedLastUpdatedBy = userStatusInfo.getUserEmail();
    String expectedLastUpdatedBySubjectId = userStatusInfo.getUserSubjectId();
    logger.info(">>Expect last updated by {}", expectedLastUpdatedBy);

    assertResourceMetadata(
        actualMetadata,
        expectedCloudPlatform,
        expectedResourceType,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        expectedResourceLineage,
        expectedCreatedBy,
        expectedLastUpdatedBy);

    // Log the clone entry in the destination workspace as that is where the cloned resource is
    // created and to record the lineage of the cloned resource id (source) to the destination
    // workspace.
    assertLatestActivityLogChangeDetails(
        expectedWorkspaceId,
        expectedLastUpdatedBy,
        expectedLastUpdatedBySubjectId,
        OperationType.CLONE,
        sourceResourceId.toString(),
        WsmResourceType.fromApiResourceType(expectedResourceType, sourceResourceStewardshipType)
            .getActivityLogChangedTarget());
  }

  public void assertLatestActivityLogChangeDetails(
      UUID workspaceId,
      String expectedActorEmail,
      String expectedActorSubjectId,
      OperationType expectedOperationType,
      String expectedChangeSubjectId,
      ActivityLogChangedTarget expectedChangeTarget) {
    ActivityLogChangeDetails actualChangedDetails =
        getLastChangeDetails(workspaceId, expectedChangeSubjectId);
    assertEquals(
        new ActivityLogChangeDetails(
            actualChangedDetails.changeDate(),
            expectedActorEmail,
            expectedActorSubjectId,
            expectedOperationType,
            expectedChangeSubjectId,
            expectedChangeTarget),
        actualChangedDetails);
  }

  /**
   * Get the latest activity log row where workspaceId matches.
   *
   * <p>Do not use WorkspaceActivityLogService#getLastUpdatedDetails because it filters out
   * non-update change_type such as `GRANT_WORKSPACE_ROLE` and `REMOVE_WORKSPACE_ROLE`.
   */
  private ActivityLogChangeDetails getLastChangeDetails(UUID workspaceId, String changeSubjectId) {
    String sql =
        """
            SELECT * FROM workspace_activity_log
            WHERE workspace_id = :workspace_id AND change_subject_id=:change_subject_id
            ORDER BY change_date DESC LIMIT 1
        """;
    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_subject_id", changeSubjectId);
    return DataAccessUtils.singleResult(
        jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER));
  }

  public void assertNoResourceWithName(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String unexpectedResourceName)
      throws Exception {
    enumerateResources(userRequest, workspaceId)
        .forEach(
            actualResource ->
                assertNotEquals(unexpectedResourceName, actualResource.getMetadata().getName()));
  }

  public void grantRole(
      AuthenticatedUserRequest userRequest, UUID workspaceId, WsmIamRole role, String memberEmail)
      throws Exception {
    var request = new ApiGrantRoleRequestBody().memberEmail(memberEmail);
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(GRANT_ROLE_PATH_FORMAT, workspaceId, role.name()))
                        .content(objectMapper.writeValueAsString(request)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

  public void removeRole(
      AuthenticatedUserRequest userRequest, UUID workspaceId, WsmIamRole role, String memberEmail)
      throws Exception {
    removeRoleInternal(userRequest, workspaceId, role, memberEmail)
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

  public void removeRoleExpectBadRequest(
      AuthenticatedUserRequest userRequest, UUID workspaceId, WsmIamRole role, String memberEmail)
      throws Exception {
    removeRoleInternal(userRequest, workspaceId, role, memberEmail)
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  private ResultActions removeRoleInternal(
      AuthenticatedUserRequest userRequest, UUID workspaceId, WsmIamRole role, String memberEmail)
      throws Exception {
    var request = new ApiGrantRoleRequestBody().memberEmail(memberEmail);
    return mockMvc.perform(
        addAuth(
            delete(String.format(REMOVE_ROLE_PATH_FORMAT, workspaceId, role.name(), memberEmail)),
            userRequest));
  }

  public void assertProperties(List<ApiProperty> expected, List<ApiProperty> actual) {
    assertThat(expected, containsInAnyOrder(actual.toArray()));
  }

  public ApiJobReport getJobReport(String path, AuthenticatedUserRequest userRequest)
      throws Exception {
    String serializedResponse =
        mockMvc
            .perform(addJsonContentType(addAuth(get(path), userRequest)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiJobResult.class).getJobReport();
  }

  private String getSerializedResponseForGet(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId) throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId)), userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String getSerializedResponseForGet(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId, UUID resourceId)
      throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId, resourceId)), userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String getSerializedResponseForGetJobResult(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId, String jobId)
      throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId, jobId)), userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public String getSerializedResponseForGetJobResult(
      AuthenticatedUserRequest userRequest, String path) throws Exception {
    return mockMvc
        .perform(addAuth(get(path), userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String getSerializedResponseForGetJobResult_error(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId, String jobId)
      throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId, jobId)), userRequest))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public String getSerializedResponseForPost(
      AuthenticatedUserRequest userRequest, String path, String request) throws Exception {
    return mockMvc
        .perform(
            addAuth(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(request),
                userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public String getSerializedResponseForPost(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId, String request)
      throws Exception {
    return mockMvc
        .perform(
            addAuth(
                post(path.formatted(workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(request),
                userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Patch http request and expect error thrown. */
  public void patchExpect(
      AuthenticatedUserRequest userRequest, String request, String api, int httpStatus)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                patch(api)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(request),
                userRequest))
        .andExpect(status().is(httpStatus));
  }

  public void postExpect(
      AuthenticatedUserRequest userRequest, String request, String api, int httpStatus)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                post(api)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(request),
                userRequest))
        .andExpect(status().is(httpStatus));
  }

  public static void assertResourceReady(ApiResourceMetadata metadata) {
    assertEquals(ApiState.READY, metadata.getState());
    assertNull(metadata.getErrorReport());
    assertNull(metadata.getJobId());
  }

  /**
   * Compare resource metadata skipping comparison of the output-only fields. For example,
   * lastUpdatedBy, state, jobId. This allows comparing the input resource to the resulting
   * resource.
   *
   * @param expectedMetadata resource metadata
   * @param actualMetadata resource metadata
   */
  public static void assertResourceMetadataEquals(
      ApiResourceMetadata expectedMetadata, ApiResourceMetadata actualMetadata) {
    assertEquals(expectedMetadata.getWorkspaceId(), actualMetadata.getWorkspaceId());
    assertEquals(expectedMetadata.getResourceId(), actualMetadata.getResourceId());
    assertEquals(expectedMetadata.getName(), actualMetadata.getName());
    assertEquals(expectedMetadata.getDescription(), actualMetadata.getDescription());
    assertEquals(expectedMetadata.getResourceType(), actualMetadata.getResourceType());
    assertEquals(expectedMetadata.getStewardshipType(), actualMetadata.getStewardshipType());
    assertEquals(expectedMetadata.getCloudPlatform(), actualMetadata.getCloudPlatform());
    assertEquals(
        expectedMetadata.getCloningInstructions(), actualMetadata.getCloningInstructions());
    assertEquals(expectedMetadata.getResourceLineage(), actualMetadata.getResourceLineage());
    assertEquals(expectedMetadata.getProperties(), actualMetadata.getProperties());

    if (expectedMetadata.getStewardshipType() == ApiStewardshipType.CONTROLLED) {
      assertEquals(
          expectedMetadata.getControlledResourceMetadata(),
          actualMetadata.getControlledResourceMetadata());
    }
  }

  public static void assertApiGcsBucketEquals(
      ApiGcpGcsBucketResource expectedBucket, ApiGcpGcsBucketResource actualBucket) {
    assertResourceMetadataEquals(expectedBucket.getMetadata(), actualBucket.getMetadata());
    assertEquals(expectedBucket.getAttributes(), actualBucket.getAttributes());
  }

  public static void assertApiBqDatasetEquals(
      ApiGcpBigQueryDatasetResource expectedDataset, ApiGcpBigQueryDatasetResource actualDataset) {
    assertResourceMetadataEquals(expectedDataset.getMetadata(), actualDataset.getMetadata());
    assertEquals(expectedDataset.getAttributes(), actualDataset.getAttributes());
  }

  public static void assertApiBqDataTableEquals(
      ApiGcpBigQueryDataTableResource expectedDataTable,
      ApiGcpBigQueryDataTableResource actualDataTable) {
    assertResourceMetadataEquals(expectedDataTable.getMetadata(), actualDataTable.getMetadata());
    assertEquals(expectedDataTable.getAttributes(), actualDataTable.getAttributes());
  }

  public static void assertApiFlexibleResourceEquals(
      ApiFlexibleResource expectedFlexibleResource, ApiFlexibleResource actualFlexibleResource) {
    assertResourceMetadataEquals(
        expectedFlexibleResource.getMetadata(), actualFlexibleResource.getMetadata());
    assertEquals(expectedFlexibleResource.getAttributes(), actualFlexibleResource.getAttributes());
  }

  public static void assertApiDataRepoEquals(
      ApiDataRepoSnapshotResource expectedDataRepo, ApiDataRepoSnapshotResource actualDataRepo) {
    assertResourceMetadataEquals(expectedDataRepo.getMetadata(), actualDataRepo.getMetadata());
    assertEquals(expectedDataRepo.getAttributes(), actualDataRepo.getAttributes());
  }

  // I can't figure out the proper way to do this
  private static Matcher<? super Integer> getExpectedCodesMatcher(List<Integer> expectedCodes) {
    if (expectedCodes.size() == 1) {
      return equalTo(expectedCodes.get(0));
    } else if (expectedCodes.size() == 2) {
      return anyOf(equalTo(expectedCodes.get(0)), equalTo(expectedCodes.get(1)));
    } else if (expectedCodes.size() == 3) {
      return anyOf(
          equalTo(expectedCodes.get(0)),
          equalTo(expectedCodes.get(1)),
          equalTo(expectedCodes.get(2)));
    } else {
      throw new RuntimeException("Unexpected number of expected codes");
    }
  }

  public ApiWsmPolicyUpdateResult updatePolicies(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    return updateRegionPolicy(userRequest, workspaceId, /*region=*/ "US");
  }

  public ApiWsmPolicyUpdateResult updateRegionPolicy(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String region) throws Exception {
    var serializedResponse =
        updatePoliciesExpect(
                userRequest,
                workspaceId,
                HttpStatus.SC_OK,
                buildWsmRegionPolicyInput(region),
                ApiWsmPolicyUpdateMode.ENFORCE_CONFLICT)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyUpdateResult.class);
  }

  public ResultActions updatePoliciesExpect(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      int code,
      ApiWsmPolicyInput addAttribute,
      ApiWsmPolicyUpdateMode updateMode)
      throws Exception {
    ApiWsmPolicyUpdateRequest updateRequest =
        new ApiWsmPolicyUpdateRequest()
            .updateMode(updateMode)
            .addAttributes(new ApiWsmPolicyInputs().addInputsItem(addAttribute));
    return mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(updateRequest)),
                userRequest))
        .andExpect(status().is(code));
  }

  public ApiRegions listValidRegions(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String platform) throws Exception {
    var serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(WORKSPACES_V1_LIST_VALID_REGIONS_PATH_FORMAT, workspaceId))
                        .queryParam("platform", platform)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8"),
                    userRequest))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiRegions.class);
  }

  public ApiWsmPolicyUpdateResult removeRegionPolicy(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String region) throws Exception {
    var serializedResponse =
        removePoliciesExpect(
                userRequest, workspaceId, HttpStatus.SC_OK, buildWsmRegionPolicyInput(region))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyUpdateResult.class);
  }

  private ResultActions removePoliciesExpect(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      int code,
      ApiWsmPolicyInput addAttribute)
      throws Exception {
    ApiWsmPolicyUpdateRequest updateRequest =
        new ApiWsmPolicyUpdateRequest()
            .updateMode(ApiWsmPolicyUpdateMode.ENFORCE_CONFLICT)
            .removeAttributes(new ApiWsmPolicyInputs().addInputsItem(addAttribute));
    return mockMvc
        .perform(
            addAuth(
                patch(String.format(UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(updateRequest)),
                userRequest))
        .andExpect(status().is(code));
  }

  public static ApiWsmPolicyInput buildWsmRegionPolicyInput(String location) {
    return new ApiWsmPolicyInput()
        .namespace("terra")
        .name("region-constraint")
        .addAdditionalDataItem(new ApiWsmPolicyPair().key("region-name").value(location));
  }

  /**
   * Test that the response is an error. If so, try to format as an error report and log it.
   * Otherwise, log what is available.
   *
   * @param response response from a mock api request
   * @return true if this was an error; false otherwise
   */
  private boolean isErrorResponse(MockHttpServletResponse response) throws Exception {
    // not an error
    if (response.getStatus() < 300) {
      return false;
    }

    String serializedResponse = response.getContentAsString();
    try {
      var errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
      logger.error("Error report: {}", errorReport);
    } catch (JsonProcessingException e) {
      logger.error("Not an error report. Serialized response is: {}", serializedResponse);
    }
    return true;
  }

  public void assertFlexibleResource(
      ApiFlexibleResource actualFlexibleResource,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedCreatedBy,
      String expectedLastUpdatedBy,
      String expectedTypeNamespace,
      String expectedType,
      @Nullable String expectedData) {
    assertResourceMetadata(
        actualFlexibleResource.getMetadata(),
        (CloudPlatform.ANY).toApiModel(),
        ApiResourceType.FLEXIBLE_RESOURCE,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedTypeNamespace, actualFlexibleResource.getAttributes().getTypeNamespace());
    assertEquals(expectedType, actualFlexibleResource.getAttributes().getType());
    assertEquals(expectedData, actualFlexibleResource.getAttributes().getData());
  }

  public void assertClonedControlledFlexibleResource(
      @NotNull ApiFlexibleResource originalFlexibleResource,
      ApiFlexibleResource actualFlexibleResource,
      UUID expectedDestWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    // Attributes are immutable upon cloning.
    ApiFlexibleResourceAttributes originalAttributes = originalFlexibleResource.getAttributes();

    assertFlexibleResource(
        actualFlexibleResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        expectedDestWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        expectedCreatedBy,
        expectedLastUpdatedBy,
        originalAttributes.getTypeNamespace(),
        originalAttributes.getType(),
        originalAttributes.getData());

    assertControlledResourceMetadata(
        actualFlexibleResource.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.SHARED_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser(),
        ApiPrivateResourceState.NOT_APPLICABLE,
        null);
  }
}
