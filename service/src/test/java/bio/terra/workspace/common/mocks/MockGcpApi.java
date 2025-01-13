package bio.terra.workspace.common.mocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpBigQueryDataTableResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpBigQueryDatasetResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpGcsBucketResourceResult;
import bio.terra.workspace.generated.model.ApiCloneReferencedGcpGcsObjectResourceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpDataprocClusterRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGceInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpDataprocClusterResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGceInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiErrorReport;
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
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateGcsBucketReferenceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.RetrieveGcsBucketCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CompleteTransferOperationStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.CopyGcsBucketDefinitionStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.RemoveBucketRolesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetBucketRolesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetNoOpBucketCloneResponseStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetReferencedDestinationGcsBucketInWorkingMapStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.SetReferencedDestinationGcsBucketResponseStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.TransferGcsBucketToGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CompleteTableCopyJobsStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CopyBigQueryDatasetDefinitionStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CopyBigQueryDatasetDifferentRegionStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CreateTableCopyJobsStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetNoOpDatasetCloneResponseStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetReferencedDestinationBigQueryDatasetInWorkingMapStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetReferencedDestinationBigQueryDatasetResponseStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceMetadataStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

@Component
public class MockGcpApi {
  private static final Logger logger = LoggerFactory.getLogger(MockGcpApi.class);

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JobService jobService;

  // GCS Bucket (Controlled)
  public static final String CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets";
  public static final String GENERATE_NAME_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT + "/generateName";
  public static final String CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT + "/%s";
  public static final String CLONE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT =
      CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT + "/clone";
  public static final String CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT + "/clone-result/%s";

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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledGcpGcsBucket.class);
  }

  public ApiGcpGcsBucketResource getControlledGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedGetResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT, workspaceId, resourceId);
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
    return mockWorkspaceV1Api.updateResourceAndExpect(
        ApiGcpGcsBucketResource.class,
        CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT,
        workspaceId,
        resourceId,
        objectMapper.writeValueAsString(requestBody),
        userRequest,
        HttpStatus.SC_OK);
  }

  public ApiCreatedControlledGcpGcsBucket cloneControlledGcsBucketAndWait(
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
        cloneControlledGcsBucketAsyncAndExpect(
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
            MockMvcUtils.JOB_SUCCESS_CODES,
            /* shouldUndo= */ false);

    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/* millis= */ 5000);
      result =
          mockWorkspaceV1Api.createResourceJobResult(
              ApiCloneControlledGcpGcsBucketResult.class,
              userRequest,
              CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT,
              sourceWorkspaceId,
              jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled GCS bucket clone of resource %s completed. ".formatted(sourceResourceId));
    return result.getBucket().getBucket();
  }

  /** Call cloneGcsBucket() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpGcsBucketResult cloneControlledGcsBucketAsyncAndExpect(
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
    Map<String, StepStatus> failureSteps = new HashMap<>();
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
        step -> failureSteps.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));
    // Because FlightDebugInfo is set on the JobService object, it applies to both the clone flight
    // and the controlled resource subflight. We cannot set lastStepFailure here or the subflight
    // will also fail. The actual last step of the clone flight depends on the cloning instructions.
    if (shouldUndo) {
      switch (cloningInstructions) {
        case NOTHING -> {
          failureSteps.put(
              SetNoOpBucketCloneResponseStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
        // Avoid undoing creation of a bucket we have copied data into by failing the flight
        // earlier
        // as the deletion can often take > 1h on the GCP side.
        case RESOURCE, DEFINITION -> {
          failureSteps.put(
              CopyGcsBucketDefinitionStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
        case REFERENCE, LINK_REFERENCE -> {
          failureSteps.put(
              SetReferencedDestinationGcsBucketResponseStep.class.getName(),
              StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
      }
    }
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(failureSteps).build());

    ApiCloneControlledGcpGcsBucketRequest request =
        new ApiCloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .name(TestUtils.appendRandomNumber("i-am-the-cloned-bucket"))
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
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(CLONE_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(MockMvcUtils.getExpectedCodesMatcher(expectedCodes)))
            .andReturn()
            .getResponse();

    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
  }

  /** Call cloneGcsBucket(), wait for flight to finish, return JobError. */
  public ApiErrorReport getCloneControlledGcsBucketResultAndExpectError(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId, int expectedCode)
      throws Exception {
    // While job is running, cloneGcsBucket returns ApiCloneControlledGcpGcsBucketResult
    // After job fails, cloneGcsBucket returns ApiCloneControlledGcpGcsBucketResult OR
    // ApiErrorReport.
    ApiCloneControlledGcpGcsBucketResult result =
        mockWorkspaceV1Api.createResourceJobResult(
            ApiCloneControlledGcpGcsBucketResult.class,
            userRequest,
            CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT,
            workspaceId,
            jobId);

    ApiErrorReport errorReport;
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/* millis= */ 3000);
      String serializedResponse =
          mockMvcUtils.getSerializedResponseForGetJobResult_error(
              userRequest, CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT, workspaceId, jobId);
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

  // GCS Bucket (Referenced)
  public static final String CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets";
  public static final String REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT =
      CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT + "/%s";
  public static final String CLONE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT =
      REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT + "/clone";

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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  public void deleteReferencedGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    mockWorkspaceV1Api.deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT);
  }

  public ApiGcpGcsBucketResource getReferencedGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT, workspaceId, resourceId);
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
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT, workspaceId, resourceId),
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
    return cloneReferencedGcsBucketAndExpect(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpGcsBucketResource cloneReferencedGcsBucketAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        mockWorkspaceV1Api.cloneReferencedResourceAndExpect(
            userRequest,
            CLONE_REFERENCED_GCP_GCS_BUCKETS_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpGcsBucketResourceResult.class)
        .getResource();
  }

  // GCS Bucket (alpha1)
  public static final String LOAD_SIGNED_URL_LIST_ALPHA_PATH_FORMAT =
      "/api/workspaces/alpha1/%s/resources/controlled/gcp/buckets/%s/load";
  public static final String LOAD_SIGNED_URL_LIST_RESULT_ALPHA_PATH_FORMAT =
      LOAD_SIGNED_URL_LIST_ALPHA_PATH_FORMAT + "/result/%s";

  // GCS Object
  public static final String CREATE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bucket/objects";
  public static final String REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT =
      CREATE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT + "/%s";
  public static final String CLONE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT =
      REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT + "/clone";

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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpGcsObjectResource.class);
  }

  public void deleteReferencedGcsObject(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    mockWorkspaceV1Api.deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT);
  }

  public ApiGcpGcsObjectResource getReferencedGcsObject(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT, workspaceId, resourceId);
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

    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT, workspaceId, resourceId),
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
    return cloneReferencedGcsObjectAndExpect(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpGcsObjectResource cloneReferencedGcsObjectAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        mockWorkspaceV1Api.cloneReferencedResourceAndExpect(
            userRequest,
            CLONE_REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);

    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpGcsObjectResourceResult.class)
        .getResource();
  }

  // BQ Dataset (Controlled)
  public static final String CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets";
  public static final String GENERATE_NAME_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT + "/generateName";
  public static final String CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT + "/%s";
  public static final String CLONE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT =
      CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT + "/clone";
  public static final String CLONE_RESULT_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT + "/clone-result/%s";

  public ApiCreatedControlledGcpBigQueryDataset createControlledBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    return createControlledBqDataset(
        userRequest,
        workspaceId,
        /* resourceName= */ TestUtils.appendRandomNumber("resource-name"),
        /* datasetName= */ TestUtils.appendRandomNumber("dataset-name"),
        /* location= */ null,
        /* defaultTableLifetime= */ null,
        /* defaultPartitionTableLifetime= */ null);
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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT,
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
    mockWorkspaceV1Api.deleteResource(
        userRequest,
        workspaceId,
        resourceId,
        StewardshipType.CONTROLLED.equals(stewardshipType)
            ? CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT
            : REFERENCED_GCP_BQ_DATASET_PATH_FORMAT);
  }

  public ApiGcpBigQueryDatasetResource getControlledBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    return getBqDataset(
        userRequest, workspaceId, resourceId, CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT);
  }

  private ApiGcpBigQueryDatasetResource getBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId, String path)
      throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(userRequest, path, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
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
    return mockWorkspaceV1Api.updateResourceAndExpect(
        ApiGcpBigQueryDatasetResource.class,
        CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT,
        workspaceId,
        resourceId,
        objectMapper.writeValueAsString(requestBody),
        userRequest,
        HttpStatus.SC_OK);
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
    return cloneControlledBqDatasetAndWait(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        destDatasetName,
        /* location= */ null,
        /* defaultTableLifetime= */ null,
        /* defaultPartitionLifetime= */ null);
  }

  public ApiGcpBigQueryDatasetResource cloneControlledBqDatasetAndWait(
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
        cloneControlledBqDatasetAsyncAndExpect(
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
            MockMvcUtils.JOB_SUCCESS_CODES,
            /* shouldUndo= */ false);

    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/* millis= */ 5000);
      result =
          mockWorkspaceV1Api.createResourceJobResult(
              ApiCloneControlledGcpBigQueryDatasetResult.class,
              userRequest,
              CLONE_RESULT_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT,
              sourceWorkspaceId,
              jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled BQ dataset clone of resource %s completed. ".formatted(sourceResourceId));
    return result.getDataset().getDataset();
  }

  /** Call cloneBigQueryDataset() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpBigQueryDatasetResult cloneControlledBqDatasetAsyncAndExpect(
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
    Map<String, StepStatus> failureSteps = new HashMap<>();
    List<Class<? extends Step>> retryableSteps =
        ImmutableList.of(
            CheckControlledResourceAuthStep.class,
            SetReferencedDestinationBigQueryDatasetInWorkingMapStep.class,
            CreateReferenceMetadataStep.class,
            SetReferencedDestinationBigQueryDatasetResponseStep.class,
            CreateTableCopyJobsStep.class,
            CompleteTableCopyJobsStep.class);
    retryableSteps.forEach(
        step -> failureSteps.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));

    // Because FlightDebugInfo is set on the JobService object, it applies to both the clone flight
    // and the controlled resource subflight. We cannot set lastStepFailure here or the subflight
    // will also fail. The actual last step of the clone flight depends on the cloning instructions.
    if (shouldUndo) {
      switch (cloningInstructions) {
        case NOTHING -> {
          failureSteps.put(
              SetNoOpDatasetCloneResponseStep.class.getName(),
              StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
        case RESOURCE -> {
          failureSteps.put(
              CopyBigQueryDatasetDifferentRegionStep.class.getName(),
              StepStatus.STEP_RESULT_FAILURE_FATAL);
          failureSteps.put(
              CompleteTableCopyJobsStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
        case DEFINITION -> {
          failureSteps.put(
              CopyBigQueryDatasetDefinitionStep.class.getName(),
              StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
        case REFERENCE, LINK_REFERENCE -> {
          failureSteps.put(
              SetReferencedDestinationBigQueryDatasetResponseStep.class.getName(),
              StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
      }
    }

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(failureSteps).build());

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
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(CLONE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(MockMvcUtils.getExpectedCodesMatcher(expectedCodes)))
            .andReturn()
            .getResponse();

    // Disable the debug info post flight
    jobService.setFlightDebugInfoForTest(null);
    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper.readValue(
        serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class);
  }

  public ApiErrorReport getCloneControlledBqDatasetResultAndExpectError(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId, int expectedCode)
      throws Exception {
    // While job is running, cloneBigQueryDataset returns ApiCloneControlledGcpBigQueryDatasetResult
    // After job fails, cloneBigQueryDataset returns ApiCloneControlledGcpBigQueryDatasetResult OR
    // ApiErrorReport.
    ApiCloneControlledGcpBigQueryDatasetResult result =
        mockWorkspaceV1Api.createResourceJobResult(
            ApiCloneControlledGcpBigQueryDatasetResult.class,
            userRequest,
            CLONE_RESULT_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT,
            workspaceId,
            jobId);

    ApiErrorReport errorReport;
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/* millis= */ 3000);
      String serializedResponse =
          mockMvcUtils.getSerializedResponseForGetJobResult_error(
              userRequest, CLONE_RESULT_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT, workspaceId, jobId);
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

  // BQ Dataset (Referenced)
  public static final String CREATE_REFERENCED_GCP_BQ_DATASETS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets";
  public static final String REFERENCED_GCP_BQ_DATASET_PATH_FORMAT =
      CREATE_REFERENCED_GCP_BQ_DATASETS_PATH_FORMAT + "/%s";
  public static final String CLONE_REFERENCED_GCP_BQ_DATASET_PATH_FORMAT =
      REFERENCED_GCP_BQ_DATASET_PATH_FORMAT + "/clone";

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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_REFERENCED_GCP_BQ_DATASETS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
  }

  public ApiGcpBigQueryDatasetResource getReferencedBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    return getBqDataset(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_BQ_DATASET_PATH_FORMAT);
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
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_BQ_DATASET_PATH_FORMAT, workspaceId, resourceId),
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
    return cloneReferencedBqDatasetAndExpect(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpBigQueryDatasetResource cloneReferencedBqDatasetAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        mockWorkspaceV1Api.cloneReferencedResourceAndExpect(
            userRequest,
            CLONE_REFERENCED_GCP_BQ_DATASET_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);
    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpBigQueryDatasetResourceResult.class)
        .getResource();
  }

  // BQ Data Table
  public static final String CREATE_REFERENCED_GCP_BQ_DATA_TABLES_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatatables";
  public static final String REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT =
      CREATE_REFERENCED_GCP_BQ_DATA_TABLES_PATH_FORMAT + "/%s";
  public static final String CLONE_REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT =
      REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT + "/clone";

  public ApiGcpBigQueryDataTableResource createReferencedBqDataTable(
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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_REFERENCED_GCP_BQ_DATA_TABLES_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  public void deleteReferencedBqDataTable(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    mockWorkspaceV1Api.deleteResource(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT);
  }

  public ApiGcpBigQueryDataTableResource getReferencedBqDataTable(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  public ApiGcpBigQueryDataTableResource updateReferencedBqDataTable(
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
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            String.format(REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT, workspaceId, resourceId),
            objectMapper.writeValueAsString(requestBody));
    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  public ApiGcpBigQueryDataTableResource cloneReferencedBqDataTable(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName)
      throws Exception {
    return cloneReferencedBqDataTableAndExpect(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        HttpStatus.SC_OK);
  }

  public ApiGcpBigQueryDataTableResource cloneReferencedBqDataTableAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      int expectedCode)
      throws Exception {
    MockHttpServletResponse response =
        mockWorkspaceV1Api.cloneReferencedResourceAndExpect(
            userRequest,
            CLONE_REFERENCED_GCP_BQ_DATA_TABLE_PATH_FORMAT,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            expectedCode);
    if (mockMvcUtils.isErrorResponse(response)) {
      return null;
    }

    String serializedResponse = response.getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiCloneReferencedGcpBigQueryDataTableResourceResult.class)
        .getResource();
  }

  // AI Notebook
  public static final String CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances";
  public static final String GENERATE_NAME_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT + "/generateName";
  public static final String CREATE_RESULT_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT + "/create-result/%s";
  public static final String CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT + "/%s";

  public ApiCreatedControlledGcpAiNotebookInstanceResult createAiNotebookInstance(
      AuthenticatedUserRequest userRequest, UUID workspaceId, @Nullable String location)
      throws Exception {
    return createAiNotebookInstanceAndWait(
        userRequest, workspaceId, /* instanceId= */ null, location);
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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        objectMapper.readValue(
            serializedResponse, ApiCreatedControlledGcpAiNotebookInstanceResult.class);

    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(5);
      result =
          mockWorkspaceV1Api.createResourceJobResult(
              ApiCreatedControlledGcpAiNotebookInstanceResult.class,
              userRequest,
              CREATE_RESULT_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT,
              workspaceId,
              jobId);
    }
    assertEquals(jobStatus, result.getJobReport().getStatus());

    return result;
  }

  // GCE Instance
  public static final String CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/gce-instances";
  public static final String GENERATE_NAME_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT + "/generateName";
  public static final String CREATE_RESULT_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT + "/create-result/%s";
  public static final String CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT + "/%s";

  public ApiCreatedControlledGcpGceInstanceResult createGceInstance(
      AuthenticatedUserRequest userRequest, UUID workspaceId, @Nullable String zone)
      throws Exception {
    return createGceInstanceAndWait(userRequest, workspaceId, /* instanceId= */ null, zone);
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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    ApiCreatedControlledGcpGceInstanceResult result =
        objectMapper.readValue(serializedResponse, ApiCreatedControlledGcpGceInstanceResult.class);

    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/* millis= */ 5000);
      result =
          mockWorkspaceV1Api.createResourceJobResult(
              ApiCreatedControlledGcpGceInstanceResult.class,
              userRequest,
              CREATE_RESULT_CONTROLLED_GCP_GCE_INSTANCES_PATH_FORMAT,
              workspaceId,
              jobId);
    }
    assertEquals(jobStatus, result.getJobReport().getStatus());

    return result;
  }

  // DataProc Cluster
  public static final String CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/dataproc-clusters";
  public static final String GENERATE_NAME_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT + "/generateName";
  public static final String CREATE_RESULT_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT + "/create-result/%s";
  public static final String CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT =
      CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT + "/%s";

  public ApiCreatedControlledGcpDataprocClusterResult createDataprocCluster(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String region,
      UUID stagingBucketId,
      UUID tempBucketId)
      throws Exception {
    return createDataprocClusterAndWait(
        userRequest, workspaceId, region, stagingBucketId, tempBucketId, /* clusterId= */ null);
  }

  public ApiCreatedControlledGcpDataprocClusterResult createDataprocClusterAndWait(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String region,
      UUID stagingBucketId,
      UUID tempBucketId,
      @Nullable String clusterId)
      throws Exception {
    return createDataprocClusterAndExpect(
        userRequest,
        workspaceId,
        region,
        stagingBucketId,
        tempBucketId,
        clusterId,
        StatusEnum.SUCCEEDED);
  }

  public ApiCreatedControlledGcpDataprocClusterResult createDataprocClusterAndExpect(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      String region,
      UUID stagingBucketId,
      UUID tempBucketId,
      @Nullable String clusterId,
      StatusEnum jobStatus)
      throws Exception {
    ApiCreateControlledGcpDataprocClusterRequestBody request =
        new ApiCreateControlledGcpDataprocClusterRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("dataproc-cluster")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .dataprocCluster(
                ControlledGcpResourceFixtures.defaultDataprocClusterCreationParameters()
                    .region(region)
                    .clusterId(
                        Optional.ofNullable(clusterId)
                            .orElse(TestUtils.appendRandomNumber("cluster-id")))
                    .configBucket(stagingBucketId)
                    .tempBucket(tempBucketId));

    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    ApiCreatedControlledGcpDataprocClusterResult result =
        objectMapper.readValue(
            serializedResponse, ApiCreatedControlledGcpDataprocClusterResult.class);

    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/* millis= */ 5000);
      result =
          mockWorkspaceV1Api.createResourceJobResult(
              ApiCreatedControlledGcpDataprocClusterResult.class,
              userRequest,
              CREATE_RESULT_CONTROLLED_GCP_DATAPROC_CLUSTERS_PATH_FORMAT,
              workspaceId,
              jobId);
    }
    assertEquals(jobStatus, result.getJobReport().getStatus());

    return result;
  }
}
