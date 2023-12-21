package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.Properties;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.UpdateWorkspaceRequestBody;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceStageModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceApiTestScriptBase;

public class WorkspaceLifecycle extends WorkspaceApiTestScriptBase {

  private static final String WORKSPACE_NAME = "name";
  private static final String WORKSPACE_DESCRIPTION = "description";

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    UUID workspaceUuid = UUID.randomUUID();

    // Perf tests run this test repeatedly. userFacingId needs to be unique for each invocation.
    // Note: These userFacingIds can't be static because UUID.randomUUID() can't be set in a static
    // variable. If a static variable called UUID.randonUUID(), uuid would be the same for some
    // invocations.
    String uuidStr = workspaceUuid.toString();
    String invalidUserFacingId = "User facing id " + uuidStr;
    String validUserFacingId = "user-facing-id-" + uuidStr;
    String validUserFacingId2 = "user-facing-id-2-" + uuidStr;

    // Create workspace
    Properties propertyMap = buildProperties(Map.of("foo", "bar", "xyzzy", "plohg"));
    CreateWorkspaceRequestBody createBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceUuid)
            .userFacingId(invalidUserFacingId)
            .stage(WorkspaceStageModel.MC_WORKSPACE)
            .properties(propertyMap);

    ApiException ex =
        assertThrows(ApiException.class, () -> workspaceApi.createWorkspace(createBody));
    assertThat(
        ex.getMessage(),
        containsString(
            "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter or number"));

    createBody.userFacingId(validUserFacingId);
    workspaceApi.createWorkspace(createBody);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE workspace");

    // assert workspace descriptions attributes after workspace creation.
    WorkspaceDescription workspaceDescription =
        workspaceApi.getWorkspace(workspaceUuid, /* minimumHighestRole= */ null);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "GET workspace");
    assertThat(workspaceDescription.getId(), equalTo(workspaceUuid));
    assertThat(workspaceDescription.getStage(), equalTo(WorkspaceStageModel.MC_WORKSPACE));
    assertNotNull(workspaceDescription.getLastUpdatedDate());

    var createdDate = workspaceDescription.getCreatedDate();
    assertNotNull(createdDate);

    var lastUpdatedBy = workspaceDescription.getLastUpdatedBy();
    assertEquals(testUser.userEmail, lastUpdatedBy);
    var createdBy = workspaceDescription.getCreatedBy();
    assertEquals(testUser.userEmail, createdBy);

    // Update workspace
    UpdateWorkspaceRequestBody updateBody =
        new UpdateWorkspaceRequestBody()
            .userFacingId(invalidUserFacingId)
            .displayName(WORKSPACE_NAME)
            .description(WORKSPACE_DESCRIPTION);
    ex =
        assertThrows(
            ApiException.class, () -> workspaceApi.updateWorkspace(updateBody, workspaceUuid));
    assertThat(
        ex.getMessage(),
        containsString(
            "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter or number"));

    updateBody.userFacingId(validUserFacingId2);
    WorkspaceDescription updatedDescription =
        workspaceApi.updateWorkspace(updateBody, workspaceUuid);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "PATCH workspace");
    assertThat(updatedDescription.getUserFacingId(), equalTo(validUserFacingId2));
    assertThat(updatedDescription.getDisplayName(), equalTo(WORKSPACE_NAME));
    assertThat(updatedDescription.getDescription(), equalTo(WORKSPACE_DESCRIPTION));
    assertNotNull(updatedDescription.getLastUpdatedDate());
    assertTrue(createdDate.isBefore(updatedDescription.getLastUpdatedDate()));

    Properties updateProperties = buildProperties(Map.of("foo", "barUpdate", "ted", "lasso"));

    workspaceApi.updateWorkspaceProperties(updateProperties, workspaceUuid);
    WorkspaceDescription updatedWorkspaceDescription =
        workspaceApi.getWorkspace(workspaceUuid, /* minimumHighestRole= */ null);
    assertTrue(
        updatedWorkspaceDescription.getProperties().contains(buildProperty("xyzzy", "plohg")));
    assertTrue(
        updatedWorkspaceDescription.getProperties().contains(buildProperty("foo", "barUpdate")));
    assertTrue(updatedWorkspaceDescription.getProperties().contains(buildProperty("ted", "lasso")));
    assertEquals(3, updatedWorkspaceDescription.getProperties().size());

    List<String> propertykey = new ArrayList<>();
    propertykey.add("xyzzy");
    workspaceApi.deleteWorkspaceProperties(propertykey, workspaceUuid);
    Properties deletedWorkspaceDescription =
        workspaceApi.getWorkspace(workspaceUuid, /* minimumHighestRole= */ null).getProperties();
    assertFalse(deletedWorkspaceDescription.contains(buildProperty("xyzzy", "plohg")));

    workspaceApi.deleteWorkspace(workspaceUuid);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "DELETE workspace");
  }

  public Properties buildProperties(Map<String, String> propertyMap) {
    Properties properties = new Properties();
    for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
      properties.add(buildProperty(entry.getKey(), entry.getValue()));
    }
    return properties;
  }

  public Property buildProperty(String key, String value) {
    Property property = new Property();
    property.setKey(key);
    property.setValue(value);
    return property;
  }
}
