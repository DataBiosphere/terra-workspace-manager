package bio.terra.workspace.app.controller;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.common.collect.ImmutableList;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertApiGcsBucketEquals;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertClonedResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertControlledResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Connected tests for controlled GCS buckets. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
//@Disabled("With the permission propagation delay, this test is just too unstable.")
@Tag("connected")
public class TempGrantTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(TempGrantTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired GcpCloudUtils cloudUtils;
  @Autowired FeatureConfiguration features;
  @Autowired CrlService crlService;
  @Autowired WorkspaceActivityLogService activityLogService;
  @Autowired SamService samService;

  private UUID workspaceId;

  @After
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void createCloudContextAndWait() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    String projectId = workspace.getGcpContext().getProjectId();
    logger.info("Created workspace {} with project {}", workspaceId, projectId);
    TimeUnit.MINUTES.sleep(60);
  }

}
