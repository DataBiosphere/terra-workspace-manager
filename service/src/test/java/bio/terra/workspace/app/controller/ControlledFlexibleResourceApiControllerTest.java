package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_FLEXIBLE_RESOURCES_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertApiFlexibleResourceEquals;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** ControlledFlexibleResourceApiController unit tests. */
public class ControlledFlexibleResourceApiControllerTest extends BaseUnitTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  private static final String defaultName = "fake-flexible-resource";
  private static final String defaultTypeNamespace = "terra";
  private static final String defaultType = "fake-flexible-type";

  @BeforeEach
  public void setUpSam() throws InterruptedException {
    // Needed for workspace creation as logging is triggered when a workspace is created in
    // `WorkspaceActivityLogHook` where we extract the user request information and log it to
    // activity log.
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(USER_REQUEST.getEmail());

    // Needed for assertion that requester has role on workspace.
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
  }

  @Test
  public void create() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    // Pass in encoded JSON data to ensure it is decoded to a JSON string within the database.

    // Create a JSON object with some data.
    JSONObject jsonData = new JSONObject();

    jsonData.put("name", "Aaron");
    jsonData.put("description", "Some description here.");
    jsonData.put("count", 23);

    JSONArray propertiesArray = new JSONArray();
    propertiesArray.put(new JSONObject().put("A", 32));
    propertiesArray.put(new JSONObject().put("B", 33));
    jsonData.put("properties", propertiesArray);

    JSONObject nestedObject = new JSONObject();
    nestedObject.put("color", "red");
    nestedObject.put("map", new JSONObject().put("goose", "bird").put("cod", "fish"));
    jsonData.put("nestedData", nestedObject);

    // Convert the object to a string.
    String expectedInputJsonString = jsonData.toString(/*indentFactor=*/ 4);

    // Encode the JSON string in base64.
    byte[] encodedJsonString = expectedInputJsonString.getBytes(StandardCharsets.UTF_8);

    var created =
        mockMvcUtils
            .createFlexibleResource(
                USER_REQUEST,
                workspaceId,
                defaultName,
                defaultTypeNamespace,
                defaultType,
                encodedJsonString)
            .getFlexibleResource();

    assertFlexibleResource(
        created,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId,
        defaultName,
        RESOURCE_DESCRIPTION,
        /*expectedCreatedBy=*/ USER_REQUEST.getEmail(),
        /*expectedLastUpdatedBy=*/ USER_REQUEST.getEmail(),
        defaultTypeNamespace,
        defaultType,
        expectedInputJsonString);

    ApiFlexibleResource gotResource =
        mockMvcUtils.getFlexibleResource(
            USER_REQUEST, workspaceId, created.getMetadata().getResourceId());
    assertApiFlexibleResourceEquals(created, gotResource);
  }

  @Test
  public void create_rejectsLargeData() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    byte[] veryLargeData = new byte[6000];
    Arrays.fill(veryLargeData, (byte) 'a');

    var request =
        mockMvcUtils.createFlexibleResourceRequestBody(
            defaultName, defaultTypeNamespace, defaultType, veryLargeData);

    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(request),
        CONTROLLED_FLEXIBLE_RESOURCES_V1_PATH_FORMAT.formatted(workspaceId),
        HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void delete() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    var resourceId =
        mockMvcUtils
            .createFlexibleResource(
                USER_REQUEST, workspaceId, defaultName, defaultTypeNamespace, defaultType, null)
            .getFlexibleResource()
            .getMetadata()
            .getResourceId();

    mockMvcUtils.getFlexibleResourceExpect(workspaceId, resourceId, HttpStatus.SC_OK);

    mockMvcUtils.deleteFlexibleResource(USER_REQUEST, workspaceId, resourceId);

    mockMvcUtils.getFlexibleResourceExpect(workspaceId, resourceId, HttpStatus.SC_NOT_FOUND);
  }

  private void assertFlexibleResource(
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
}
