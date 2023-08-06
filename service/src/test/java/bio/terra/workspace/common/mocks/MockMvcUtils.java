package bio.terra.workspace.common.mocks;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_RESOURCE_PROPERTIES;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_SUBJECT_ID;
import static bio.terra.workspace.db.WorkspaceActivityLogDao.ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceLineageEntry;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiState;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Assertions;
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
  private static final Logger logger = LoggerFactory.getLogger(MockMvcUtils.class);

  // Only use this if you are mocking SAM. If you're using real SAM,
  // use userAccessUtils.defaultUserAuthRequest() instead.
  public static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          DEFAULT_USER_EMAIL, DEFAULT_USER_SUBJECT_ID, Optional.of("ThisIsNotARealBearerToken"));
  public static final List<Integer> JOB_SUCCESS_CODES =
      List.of(HttpStatus.SC_OK, HttpStatus.SC_ACCEPTED);

  // Do not Autowire UserAccessUtils. UserAccessUtils are for connected tests and not unit tests
  // (since unit tests don't use real SAM). Instead, each method must take in userRequest.
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private SamService samService;

  // API utils

  public static MockHttpServletRequestBuilder addAuth(
      MockHttpServletRequestBuilder request, AuthenticatedUserRequest userRequest) {
    return request.header("Authorization", "Bearer " + userRequest.getRequiredToken());
  }

  public static MockHttpServletRequestBuilder addJsonContentType(
      MockHttpServletRequestBuilder request) {
    return request.contentType("application/json");
  }

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

  /**
   * Test that the response is an error. If so, try to format as an error report and log it.
   * Otherwise, log what is available.
   *
   * @param response response from a mock api request
   * @return true if this was an error; false otherwise
   */
  public boolean isErrorResponse(MockHttpServletResponse response) throws Exception {
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

  public String getSerializedResponseForGet(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId) throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId)), userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public String getSerializedResponseForGet(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId, UUID resourceId)
      throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId, resourceId)), userRequest))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  public String getSerializedResponseForGetJobResult(
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

  public String getSerializedResponseForGetJobResult_error(
      AuthenticatedUserRequest userRequest, String path, UUID workspaceId, String jobId)
      throws Exception {
    return mockMvc
        .perform(addAuth(get(path.formatted(workspaceId, jobId)), userRequest))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  // Job utils

  public <T> T getCheckedJobResult(MockHttpServletResponse response, Class<T> clazz)
      throws Exception {
    int statusCode = response.getStatus();
    String content = response.getContentAsString();
    if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_ACCEPTED) {
      return objectMapper.readValue(content, clazz);
    }
    Assertions.fail(
        String.format("Expected OK or ACCEPTED, but received %d; body: %s", statusCode, content));
    return null;
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

  // Resources

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

  public static void assertResourceReady(ApiResourceMetadata metadata) {
    assertEquals(ApiState.READY, metadata.getState());
    assertNull(metadata.getErrorReport());
    assertNull(metadata.getJobId());
  }

  // Misc. utils

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
            workspaceId,
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

  public static Matcher<? super Integer> getExpectedCodesMatcher(List<Integer> expectedCodes) {
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
}
