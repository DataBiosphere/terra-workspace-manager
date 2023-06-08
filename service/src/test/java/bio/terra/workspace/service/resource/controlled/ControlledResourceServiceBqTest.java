package bio.terra.workspace.service.resource.controlled;

import static bio.terra.workspace.common.testfixtures.ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder;
import static bio.terra.workspace.common.testfixtures.ControlledGcpResourceFixtures.uniqueDatasetId;
import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.makeDefaultControlledResourceFields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.testutils.StairwayTestUtils;
import bio.terra.workspace.connected.UserAccessTestUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.CreateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.DeleteBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.UpdateBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteMetadataStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledResourceServiceBqTest extends BaseConnectedTest {
  private static final String DEFAULT_DATASET_LOCATION = "us-central1";

  // Store workspaceId instead of workspace so that for local development, one can easily use a
  // previously created workspace.
  private UUID workspaceId;
  private UserAccessTestUtils.TestUser user;
  private String projectId;

  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crlService;
  @Autowired private FeatureConfiguration features;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessTestUtils userAccessTestUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private ResourceDao resourceDao;
  @Autowired private WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired private WsmResourceService wsmResourceService;

  @BeforeAll
  public void setup() {
    user = userAccessTestUtils.defaultUser();
    workspaceId =
        workspaceUtils
            .createWorkspaceWithGcpContext(userAccessTestUtils.defaultUserAuthRequest())
            .getWorkspaceId();
    projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceId, user.getAuthenticatedRequest());
  }

  /** After running all tests, delete the shared workspace. */
  @AfterAll
  public void cleanUp() {
    user = userAccessTestUtils.defaultUser();
    Workspace workspace = workspaceService.getWorkspace(workspaceId);
    workspaceService.deleteWorkspace(workspace, user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGetUpdateDeleteBqDataset() throws Exception {
    String datasetId = "my_test_dataset";
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(resource.partialEqual(createdDataset));

    ControlledBigQueryDatasetResource fetchedDataset =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(resource.partialEqual(fetchedDataset));

    String newName = "NEW_createGetUpdateDeleteBqDataset";
    String newDescription = "new resource description";
    long newDefaultTableLifetime = 3600L;
    long newDefaultPartitionLifetime = 3601L;
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(newDefaultTableLifetime)
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    wsmResourceService.updateResource(
        userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
        fetchedDataset,
        new CommonUpdateParameters().setName(newName).setDescription(newDescription),
        updateParameters);

    ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertEquals(newName, updatedResource.getName());
    assertEquals(newDescription, updatedResource.getDescription());

    Dataset updatedDatasetFromCloud =
        crlService.createWsmSaBigQueryCow().datasets().get(projectId, datasetId).execute();
    assertEquals(
        newDefaultTableLifetime * 1000L, updatedDatasetFromCloud.getDefaultTableExpirationMs());
    assertEquals(
        newDefaultPartitionLifetime * 1000L,
        updatedDatasetFromCloud.getDefaultPartitionExpirationMs());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(workspaceId, resource.getResourceId()));

    features.setAlpha1Enabled(true);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceId, user.getAuthenticatedRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createBqDatasetDo() throws Exception {
    String datasetId = uniqueDatasetId();
    String location = "us-central1";
    long defaultTableLifetimeSec = 5900L;
    long defaultPartitionLifetimeSec = 5901L;
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(location)
            .defaultTableLifetime(defaultTableLifetimeSec)
            .defaultPartitionLifetime(defaultPartitionLifetimeSec);
    ControlledBigQueryDatasetResource resource =
        makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .datasetName(datasetId)
            .build();

    // Test idempotency of dataset-specific step by retrying it once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(resource.partialEqual(createdDataset));

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset =
        bqCow.datasets().get(projectId, createdDataset.getDatasetName()).execute();
    assertEquals(location, cloudDataset.getLocation());
    assertEquals(defaultTableLifetimeSec * 1000L, cloudDataset.getDefaultTableExpirationMs());
    assertEquals(
        defaultPartitionLifetimeSec * 1000L, cloudDataset.getDefaultPartitionExpirationMs());

    assertTrue(
        resource.partialEqual(
            controlledResourceService.getControlledResource(
                workspaceId, resource.getResourceId())));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createBqDatasetUndo() {
    String datasetId = uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .datasetName(datasetId)
            .build();

    // Test idempotency of datatset-specific undo step by retrying once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            // Fail after the last step to test that everything is deleted on undo.
            .lastStepFailure(true)
            .undoStepFailures(retrySteps)
            .build());

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters));

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    GoogleJsonResponseException getException =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bqCow.datasets().get(projectId, resource.getDatasetName()).execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), getException.getStatusCode());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(workspaceId, resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteBqDatasetDo() {
    String datasetId = uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(resource.partialEqual(createdDataset));

    // Test idempotency of delete by retrying steps once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(DeleteMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(DeleteBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    // Do not test lastStepFailure, as this flight has no undo steps, only dismal failure.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    controlledResourceService.deleteControlledResourceSync(
        resource.getWorkspaceId(), resource.getResourceId(), user.getAuthenticatedRequest());

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    GoogleJsonResponseException getException =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bqCow.datasets().get(projectId, resource.getDatasetName()).execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), getException.getStatusCode());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(workspaceId, resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteBqDatasetUndo() {
    String datasetId = uniqueDatasetId();
    String location = "us-central1";

    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters().datasetId(datasetId).location(location);
    ControlledBigQueryDatasetResource resource =
        makeDefaultControlledBqDatasetBuilder(workspaceId)
            .projectId(projectId)
            .datasetName(datasetId)
            .build();

    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(resource.partialEqual(createdDataset));

    // None of the steps on this flight are undoable, so even with lastStepFailure set to true we
    // should expect the resource to really be deleted.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());

    assertThrows(
        InvalidResultStateException.class,
        () ->
            controlledResourceService.deleteControlledResourceSync(
                resource.getWorkspaceId(),
                resource.getResourceId(),
                user.getAuthenticatedRequest()));

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    GoogleJsonResponseException getException =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> bqCow.datasets().get(projectId, resource.getDatasetName()).execute());
    assertEquals(HttpStatus.NOT_FOUND.value(), getException.getStatusCode());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            controlledResourceService.getControlledResource(workspaceId, resource.getResourceId()));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetDo() throws Exception {
    // create the dataset
    Long initialDefaultTableLifetime = 5900L;
    Long initialDefaultPartitionLifetime = 5901L;
    ControlledBigQueryDatasetResource resource =
        createBigQueryResource(initialDefaultTableLifetime, initialDefaultPartitionLifetime);

    // Test idempotency of dataset-specific steps by retrying them once.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(UpdateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build());

    // update the dataset
    String newName = "NEW_updateBqDatasetDo";
    String newDescription = "new resource description";
    Long newDefaultTableLifetime = 3600L;
    Long newDefaultPartitionLifetime = 3601L;
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(newDefaultTableLifetime)
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    wsmResourceService.updateResource(
        userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
        resource,
        new CommonUpdateParameters().setName(newName).setDescription(newDescription),
        updateParameters);

    // check the properties stored on the cloud were updated
    validateBigQueryDatasetCloudMetadata(
        projectId, resource.getDatasetName(), newDefaultTableLifetime, newDefaultPartitionLifetime);

    // check the properties stored in WSM were updated
    ControlledBigQueryDatasetResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertEquals(newName, fetchedResource.getName());
    assertEquals(newDescription, fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetUndo() throws Exception {
    Long initialDefaultTableLifetime = 4800L;
    Long initialDefaultPartitionLifetime = 4801L;
    ControlledBigQueryDatasetResource resource =
        createBigQueryResource(initialDefaultTableLifetime, initialDefaultPartitionLifetime);

    // Test idempotency of BQ-specific steps by forcing an error and retrying on the undo path
    Map<String, StepStatus> doRetrySteps = new HashMap<>();
    doRetrySteps.put(
        UpdateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);
    Map<String, StepStatus> undoRetrySteps = new HashMap<>();
    undoRetrySteps.put(
        UpdateBigQueryDatasetStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(doRetrySteps)
            .undoStepFailures(undoRetrySteps)
            .build());

    // update the dataset
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(3600L)
            .defaultPartitionLifetime(3601L);

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () ->
            wsmResourceService.updateResource(
                userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
                resource,
                new CommonUpdateParameters()
                    .setName("NEW_updateBqDatasetUndo")
                    .setDescription("new resource description"),
                updateParameters));

    // check the properties stored on the cloud were not updated
    validateBigQueryDatasetCloudMetadata(
        projectId,
        resource.getDatasetName(),
        initialDefaultTableLifetime,
        initialDefaultPartitionLifetime);

    // check the properties stored in WSM were not updated
    ControlledBigQueryDatasetResource fetchedResource =
        controlledResourceService
            .getControlledResource(workspaceId, resource.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertEquals(resource.getName(), fetchedResource.getName());
    assertEquals(resource.getDescription(), fetchedResource.getDescription());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetWithUndefinedExpirationTimes() throws Exception {
    Long initialDefaultTableLifetime = 4800L;
    Long initialDefaultPartitionLifetime = 4801L;
    ControlledBigQueryDatasetResource resource =
        createBigQueryResource(initialDefaultTableLifetime, initialDefaultPartitionLifetime);

    // check the expiration times stored on the cloud are defined
    validateBigQueryDatasetCloudMetadata(
        projectId,
        resource.getDatasetName(),
        initialDefaultTableLifetime,
        initialDefaultPartitionLifetime);

    // make an update request to set the expiration times to undefined values
    ApiGcpBigQueryDatasetUpdateParameters updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(0L)
            .defaultPartitionLifetime(0L);

    wsmResourceService.updateResource(
        userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
        resource,
        new CommonUpdateParameters(),
        updateParameters);

    // check the expiration times stored on the cloud are now undefined
    validateBigQueryDatasetCloudMetadata(projectId, resource.getDatasetName(), null, null);

    // update just one expiration time back to a defined value
    Long newDefaultTableLifetime = 3600L;
    updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters().defaultTableLifetime(newDefaultTableLifetime);
    wsmResourceService.updateResource(
        userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
        resource,
        new CommonUpdateParameters(),
        updateParameters);

    // check there is one defined and one undefined expiration value
    validateBigQueryDatasetCloudMetadata(
        projectId, resource.getDatasetName(), newDefaultTableLifetime, null);

    // update the other expiration time back to a defined value
    Long newDefaultPartitionLifetime = 3601L;
    updateParameters =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultPartitionLifetime(newDefaultPartitionLifetime);
    wsmResourceService.updateResource(
        userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
        resource,
        new CommonUpdateParameters(),
        updateParameters);

    // check the expiration times stored on the cloud are both defined again
    validateBigQueryDatasetCloudMetadata(
        projectId, resource.getDatasetName(), newDefaultTableLifetime, newDefaultPartitionLifetime);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void updateBqDatasetWithInvalidExpirationTimes() throws Exception {
    // create the dataset, with expiration times initially undefined
    ControlledBigQueryDatasetResource resource = createBigQueryResource(null, null);

    try {
      // make an update request to set the table expiration time to an invalid value (<3600)
      final ApiGcpBigQueryDatasetUpdateParameters updateParameters =
          new ApiGcpBigQueryDatasetUpdateParameters()
              .defaultTableLifetime(3000L)
              .defaultPartitionLifetime(3601L);
      assertThrows(
          BadRequestException.class,
          () ->
              wsmResourceService.updateResource(
                  userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
                  resource,
                  new CommonUpdateParameters(),
                  updateParameters));

      // check the expiration times stored on the cloud are still undefined,
      // because the update above failed
      validateBigQueryDatasetCloudMetadata(projectId, resource.getDatasetName(), null, null);

      // make another update request to set the partition expiration time to an invalid value (<0)
      final ApiGcpBigQueryDatasetUpdateParameters updateParameters2 =
          new ApiGcpBigQueryDatasetUpdateParameters()
              .defaultTableLifetime(3600L)
              .defaultPartitionLifetime(-2L);
      assertThrows(
          BadRequestException.class,
          () ->
              wsmResourceService.updateResource(
                  userAccessTestUtils.defaultUser().getAuthenticatedRequest(),
                  resource,
                  new CommonUpdateParameters(),
                  updateParameters2));

      // check the expiration times stored on the cloud are still undefined,
      // because the update above failed
      validateBigQueryDatasetCloudMetadata(projectId, resource.getDatasetName(), null, null);

    } finally {
      // Remove dataset to not conflict with other test that checks for empty lifetime
      controlledResourceService.deleteControlledResourceSync(
          workspaceId, resource.getResourceId(), userAccessTestUtils.defaultUserAuthRequest());
    }
  }

  /**
   * Create the starter big query resource for the update tests
   *
   * @param initialDefaultTableLifetime read the
   * @param initialDefaultPartitionLifetime parameter name
   * @return and guess
   */
  private ControlledBigQueryDatasetResource createBigQueryResource(
      Long initialDefaultTableLifetime, Long initialDefaultPartitionLifetime) {
    String datasetId = uniqueDatasetId();
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId(datasetId)
            .location(DEFAULT_DATASET_LOCATION)
            .defaultTableLifetime(initialDefaultTableLifetime)
            .defaultPartitionLifetime(initialDefaultPartitionLifetime);
    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .common(makeDefaultControlledResourceFields(workspaceId))
            .projectId(projectId)
            .datasetName(datasetId)
            .defaultTableLifetime(initialDefaultTableLifetime)
            .defaultPartitionLifetime(initialDefaultPartitionLifetime)
            .build();
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, user.getAuthenticatedRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    assertTrue(resource.partialEqual(createdDataset));
    return resource;
  }

  /**
   * Lookup the location and expiration times stored on the cloud for a BigQuery dataset, and assert
   * they match the given values.
   */
  private void validateBigQueryDatasetCloudMetadata(
      String projectId,
      String datasetId,
      Long defaultTableExpirationSec,
      Long defaultPartitionExpirationSec)
      throws IOException {
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    Dataset cloudDataset = bqCow.datasets().get(projectId, datasetId).execute();

    assertEquals(DEFAULT_DATASET_LOCATION, cloudDataset.getLocation());

    if (defaultTableExpirationSec == null) {
      assertNull(cloudDataset.getDefaultTableExpirationMs());
    } else {
      assertEquals(defaultTableExpirationSec * 1000, cloudDataset.getDefaultTableExpirationMs());
    }

    if (defaultPartitionExpirationSec == null) {
      assertNull(cloudDataset.getDefaultPartitionExpirationMs());
    } else {
      assertEquals(
          defaultPartitionExpirationSec * 1000, cloudDataset.getDefaultPartitionExpirationMs());
    }
  }
}
