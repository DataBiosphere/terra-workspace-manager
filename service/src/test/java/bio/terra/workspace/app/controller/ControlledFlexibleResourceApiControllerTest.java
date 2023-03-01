package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertApiFlexibleResourceEquals;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** ControlledFlexibleResourceApiController unit tests. */
public class ControlledFlexibleResourceApiControllerTest extends BaseUnitTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;

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

    var created =
        mockMvcUtils
            .createFlexibleResource(
                USER_REQUEST,
                workspaceId,
                "fake-flexible-resource",
                "terra",
                "fake-flexible-type",
                "fake-data".getBytes())
            .getFlexibleResource();

    assertFlexibleResource(
        created,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId,
        "fake-flexible-resource",
        RESOURCE_DESCRIPTION,
        /*expectedCreatedBy=*/ USER_REQUEST.getEmail(),
        /*expectedLastUpdatedBy=*/ USER_REQUEST.getEmail(),
        "terra",
        "fake-flexible-type",
        "fake-data".getBytes());

    ApiFlexibleResource gotResource =
        mockMvcUtils.getFlexibleResource(
            USER_REQUEST, workspaceId, created.getMetadata().getResourceId());
    assertApiFlexibleResourceEquals(created, gotResource);
  }

  @Test
  public void delete() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    var resourceId =
        mockMvcUtils
            .createFlexibleResource(
                USER_REQUEST,
                workspaceId,
                "fake-flexible-resource",
                "terra",
                "fake-flexible-type",
                null)
            .getFlexibleResource()
            .getMetadata()
            .getResourceId();

    mockMvc
        .perform(
            addAuth(
                get(CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT.formatted(workspaceId, resourceId)),
                USER_REQUEST))
        .andExpect(status().is2xxSuccessful());

    mockMvcUtils.deleteFlexibleResource(USER_REQUEST, workspaceId, resourceId);

    mockMvc
        .perform(
            addAuth(
                get(CONTROLLED_FLEXIBLE_RESOURCE_V1_PATH_FORMAT.formatted(workspaceId, resourceId)),
                USER_REQUEST))
        .andExpect(status().is4xxClientError());
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
      @Nullable byte[] expectedData) {
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
    assertArrayEquals(expectedData, actualFlexibleResource.getAttributes().getData());
  }
}
