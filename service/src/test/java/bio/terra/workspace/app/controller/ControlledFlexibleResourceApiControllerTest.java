package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.mocks.MockFlexibleResourceApi.CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.mocks.MockFlexibleResourceApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockFlexibleResourceApi mockFlexibleResourceApi;
  @Autowired ObjectMapper objectMapper;
  private static final String defaultDecodedData = "{\"name\":\"original JSON\"}";
  private static final String defaultNewDecodedData = "{\"description\":\"this is new JSON\"}";
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
    when(mockSamService()
            .isAuthorized(
                any(),
                eq(SamConstants.SamResource.SPEND_PROFILE),
                any(),
                eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    // Needed for assertion that requester has role on workspace.
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));

    when(mockFeatureConfiguration().getStateRule())
        .thenReturn(WsmResourceStateRule.DELETE_ON_FAILURE);
  }

  @Test
  public void create() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

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
    String expectedInputJsonString = jsonData.toString(/* indentFactor= */ 4);

    // Encode the JSON string in base64.
    byte[] encodedJsonString = expectedInputJsonString.getBytes(StandardCharsets.UTF_8);

    ApiFlexibleResource createdFlexResource =
        createDefaultFlexResourceWithData(workspaceId, encodedJsonString).getFlexibleResource();

    MockFlexibleResourceApi.assertFlexibleResource(
        createdFlexResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId,
        defaultName,
        RESOURCE_DESCRIPTION,
        /* expectedCreatedBy= */ USER_REQUEST.getEmail(),
        /* expectedLastUpdatedBy= */ USER_REQUEST.getEmail(),
        defaultTypeNamespace,
        defaultType,
        expectedInputJsonString);

    ApiFlexibleResource gotResource =
        mockFlexibleResourceApi.getFlexibleResource(
            USER_REQUEST, workspaceId, createdFlexResource.getMetadata().getResourceId());
    MockFlexibleResourceApi.assertApiFlexibleResourceEquals(createdFlexResource, gotResource);
  }

  @Test
  public void create_rejectsLargeData() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    byte[] veryLargeData = new byte[6000];
    Arrays.fill(veryLargeData, (byte) 'a');

    ApiCreateControlledFlexibleResourceRequestBody request =
        mockFlexibleResourceApi.createFlexibleResourceRequestBody(
            defaultName, defaultTypeNamespace, defaultType, veryLargeData);

    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(request),
        CREATE_CONTROLLED_FLEXIBLE_RESOURCES_PATH_FORMAT.formatted(workspaceId),
        HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void update() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    ApiFlexibleResource flexResource = createDefaultFlexResource(workspaceId).getFlexibleResource();
    UUID resourceId = flexResource.getMetadata().getResourceId();
    String newName = "new-flex-resource-name";
    String newDescription = "This is an updated description";

    byte[] encodedNewData = defaultNewDecodedData.getBytes(StandardCharsets.UTF_8);

    // Ensure cloning instructions are updated. The default COPY_INSTRUCTION for controlled
    // resources in tests is DEFINITION.
    assertEquals(
        ApiCloningInstructionsEnum.DEFINITION, flexResource.getMetadata().getCloningInstructions());

    ApiFlexibleResource updatedFlex =
        mockFlexibleResourceApi.updateFlexibleResource(
            workspaceId,
            resourceId,
            newName,
            newDescription,
            encodedNewData,
            ApiCloningInstructionsEnum.NOTHING);

    assertEquals(newName, updatedFlex.getMetadata().getName());
    assertEquals(newDescription, updatedFlex.getMetadata().getDescription());
    assertEquals(defaultNewDecodedData, updatedFlex.getAttributes().getData());
    assertEquals(defaultType, updatedFlex.getAttributes().getType());
    assertEquals(defaultTypeNamespace, updatedFlex.getAttributes().getTypeNamespace());
    assertEquals(
        ApiCloningInstructionsEnum.NOTHING, updatedFlex.getMetadata().getCloningInstructions());
  }

  @Test
  public void update_onlyNameAndDescription() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    UUID resourceId = createDefaultFlexResource(workspaceId).getResourceId();
    String newName = "new-flex-resource-name";
    String newDescription = "This is an updated description";

    ApiFlexibleResource updatedFlex =
        mockFlexibleResourceApi.updateFlexibleResource(
            workspaceId, resourceId, newName, newDescription, null, null);

    assertEquals(newName, updatedFlex.getMetadata().getName());
    assertEquals(newDescription, updatedFlex.getMetadata().getDescription());
    assertEquals(defaultDecodedData, updatedFlex.getAttributes().getData());
    assertEquals(defaultType, updatedFlex.getAttributes().getType());
    assertEquals(defaultTypeNamespace, updatedFlex.getAttributes().getTypeNamespace());
  }

  @Test
  public void update_onlyData() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    ApiFlexibleResource originalFlex = createDefaultFlexResource(workspaceId).getFlexibleResource();
    UUID resourceId = originalFlex.getMetadata().getResourceId();
    String originalDescription = originalFlex.getMetadata().getDescription();

    byte[] encodedNewData = defaultNewDecodedData.getBytes(StandardCharsets.UTF_8);

    ApiFlexibleResource updatedFlex =
        mockFlexibleResourceApi.updateFlexibleResource(
            workspaceId, resourceId, null, null, encodedNewData, null);

    assertEquals(defaultName, updatedFlex.getMetadata().getName());
    assertEquals(originalDescription, updatedFlex.getMetadata().getDescription());
    assertEquals(defaultNewDecodedData, updatedFlex.getAttributes().getData());
    assertEquals(defaultType, updatedFlex.getAttributes().getType());
    assertEquals(defaultTypeNamespace, updatedFlex.getAttributes().getTypeNamespace());
  }

  @Test
  public void update_rejectsLargeData() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    UUID resourceId = createDefaultFlexResource(workspaceId).getResourceId();

    byte[] veryLargeData = new byte[6000];
    Arrays.fill(veryLargeData, (byte) 'a');

    mockFlexibleResourceApi.updateFlexibleResourceExpect(
        workspaceId, resourceId, null, null, veryLargeData, null, HttpStatus.SC_BAD_REQUEST);
  }

  private ApiCreatedControlledFlexibleResource createDefaultFlexResource(UUID workspaceId)
      throws Exception {
    byte[] originalEncodedData = defaultDecodedData.getBytes(StandardCharsets.UTF_8);
    return createDefaultFlexResourceWithData(workspaceId, originalEncodedData);
  }

  private ApiCreatedControlledFlexibleResource createDefaultFlexResourceWithData(
      UUID workspaceId, byte[] data) throws Exception {
    return mockFlexibleResourceApi.createFlexibleResource(
        USER_REQUEST, workspaceId, defaultName, defaultTypeNamespace, defaultType, data);
  }

  @Test
  public void delete() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    UUID resourceId = createDefaultFlexResourceWithData(workspaceId, null).getResourceId();

    mockFlexibleResourceApi.getFlexibleResourceExpect(workspaceId, resourceId, HttpStatus.SC_OK);
    mockFlexibleResourceApi.deleteFlexibleResource(USER_REQUEST, workspaceId, resourceId);
    mockFlexibleResourceApi.getFlexibleResourceExpect(
        workspaceId, resourceId, HttpStatus.SC_NOT_FOUND);
  }

  @Test
  void clone_copyNothing() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    UUID resourceId = createDefaultFlexResourceWithData(workspaceId, null).getResourceId();

    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiFlexibleResource clonedFlexResource =
        mockFlexibleResourceApi.cloneFlexibleResourceAndWait(
            USER_REQUEST,
            /* sourceWorkspaceId= */ workspaceId,
            resourceId,
            /* destWorkspaceId= */ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName,
            null);
    assertNull(clonedFlexResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockWorkspaceV1Api.assertNoResourceWithName(USER_REQUEST, workspaceId, destResourceName);
  }
}
