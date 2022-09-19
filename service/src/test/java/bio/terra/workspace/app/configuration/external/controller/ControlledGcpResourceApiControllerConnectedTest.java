package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.createBigQueryDataset;
import static bio.terra.workspace.common.utils.MockMvcUtils.createGcsBucket;
import static bio.terra.workspace.common.utils.MockMvcUtils.deleteWorkspace;
import static bio.terra.workspace.common.utils.MockMvcUtils.getBigQueryDataset;
import static bio.terra.workspace.common.utils.MockMvcUtils.getGcsBucket;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class ControlledGcpResourceApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired WorkspaceConnectedTestUtils connectedTestUtils;

  private UUID workspaceId;

  @BeforeEach
  public void setUp() throws Exception {
    workspaceId =
        connectedTestUtils
            .createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest())
            .getWorkspaceId();
  }

  @AfterEach
  public void cleanup() throws Exception {
    deleteWorkspace(workspaceId, mockMvc, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  public void createControlledBigQueryDataset() throws Exception {
    ApiCreatedControlledGcpBigQueryDataset resource =
        createBigQueryDataset(
            mockMvc, objectMapper, workspaceId, userAccessUtils.defaultUserAuthRequest());

    ApiGcpBigQueryDatasetResource retrievedResource =
        getBigQueryDataset(
            mockMvc,
            objectMapper,
            workspaceId,
            resource.getResourceId(),
            userAccessUtils.defaultUserAuthRequest());

    assertEquals(resource.getBigQueryDataset(), retrievedResource);
  }

  @Test
  public void createControlledGcsBucket() throws Exception {
    ApiCreatedControlledGcpGcsBucket resource =
        createGcsBucket(
            mockMvc, objectMapper, workspaceId, userAccessUtils.defaultUserAuthRequest());

    ApiGcpGcsBucketResource retrievedResource =
        getGcsBucket(
            mockMvc,
            objectMapper,
            workspaceId,
            resource.getResourceId(),
            userAccessUtils.defaultUserAuthRequest());

    assertEquals(resource.getGcpBucket(), retrievedResource);
  }
}
