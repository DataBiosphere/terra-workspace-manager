package bio.terra.workspace.common.mocks;

import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiCloneControlledFlexibleResourceRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledFlexibleResourceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiControlledFlexibleResourceCreationParameters;
import bio.terra.workspace.generated.model.ApiCreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResourceUpdateParameters;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiUpdateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.flexibleresource.CloneFlexibleResourceStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
public class MockFlexibleResourceApi {
  private static final Logger logger = LoggerFactory.getLogger(MockFlexibleResourceApi.class);

  public static final String CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/any/flexibleResources";
  public static final String CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT =
      CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT + "/%s";
  public static final String CLONE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT =
      CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT + "/clone";

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JobService jobService;

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
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedControlledFlexibleResource.class);
  }

  public void deleteFlexibleResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    mockWorkspaceV1Api.deleteResource(
        userRequest, workspaceId, resourceId, CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT);
  }

  public ApiFlexibleResource getFlexibleResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            userRequest, CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT, workspaceId, resourceId);
    return objectMapper.readValue(serializedResponse, ApiFlexibleResource.class);
  }

  public void getFlexibleResourceExpect(UUID workspaceId, UUID resourceId, int httpStatus)
      throws Exception {
    mockMvc
        .perform(
            MockMvcUtils.addAuth(
                get(CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT.formatted(workspaceId, resourceId)),
                USER_REQUEST))
        .andExpect(status().is(httpStatus));
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

    return mockWorkspaceV1Api.updateResourceAndExpect(
        ApiFlexibleResource.class,
        CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT,
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

    mockWorkspaceV1Api.updateResourceAndExpect(
        ApiFlexibleResource.class,
        CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT,
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

  public ApiFlexibleResource cloneFlexibleResourceAndWait(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDescription)
      throws Exception {
    ApiCloneControlledFlexibleResourceResult result =
        cloneFlexibleResourceAndExpect(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destDescription,
            // clone_copyNothing sometimes returns SC_OK, even for the initial call. So accept both
            // to avoid flakes.
            MockMvcUtils.JOB_SUCCESS_CODES,
            /* shouldUndo= */ false);
    logger.info("Controlled flex clone of resource %s completed.".formatted(sourceResourceId));
    return result.getResource();
  }

  public ApiCloneControlledFlexibleResourceResult cloneFlexibleResourceAndExpect(
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
    Map<String, StepStatus> failureSteps = new HashMap<>();
    List<Class<? extends Step>> retryableSteps =
        ImmutableList.of(CheckControlledResourceAuthStep.class);
    retryableSteps.forEach(
        step -> failureSteps.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));

    if (shouldUndo) {
      failureSteps.put(
          CloneFlexibleResourceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().doStepFailures(failureSteps).build());

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
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(CLONE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT.formatted(
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
    return objectMapper.readValue(
        serializedResponse, ApiCloneControlledFlexibleResourceResult.class);
  }

  public static void assertFlexibleResource(
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
    MockMvcUtils.assertResourceMetadata(
        actualFlexibleResource.getMetadata(),
        (CloudPlatform.ANY).toApiModel(),
        ApiResourceType.FLEXIBLE_RESOURCE,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /* expectedResourceLineage= */ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedTypeNamespace, actualFlexibleResource.getAttributes().getTypeNamespace());
    assertEquals(expectedType, actualFlexibleResource.getAttributes().getType());
    assertEquals(expectedData, actualFlexibleResource.getAttributes().getData());
  }

  public static void assertApiFlexibleResourceEquals(
      ApiFlexibleResource expectedFlexibleResource, ApiFlexibleResource actualFlexibleResource) {
    MockMvcUtils.assertResourceMetadataEquals(
        expectedFlexibleResource.getMetadata(), actualFlexibleResource.getMetadata());
    assertEquals(expectedFlexibleResource.getAttributes(), actualFlexibleResource.getAttributes());
  }
}
