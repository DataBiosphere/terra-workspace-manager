package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.mocks.MockAzureApi.CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureSasBundle;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasTokenOptions;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class CreateAzureStorageContainerSasTokenTest extends BaseAzureUnitTest {
  @Autowired MockMvc mockMvc;
  @Autowired AzureConfiguration azureConfiguration;

  private UUID workspaceId;
  private UUID storageContainerId;

  final ArgumentCaptor<SasTokenOptions> sasTokenOptionsCaptor =
      ArgumentCaptor.forClass(SasTokenOptions.class);

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    storageContainerId = UUID.randomUUID();

    when(mockAzureStorageAccessService()
            .createAzureStorageContainerSasToken(
                eq(workspaceId), eq(storageContainerId), any(), any()))
        .thenReturn(new AzureSasBundle("sasToken", "sasUrl", "abc123"));
  }

  @Test
  void createSASToken400BadDuration() throws Exception {
    azureConfiguration.setSasTokenExpiryTimeMaximumMinutesOffset(
        240L); // maximum of 240 minutes (4 hours)

    // expiration duration must be positive
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasExpirationDuration", "-1"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));

    // expiration can't exceed 4 hours = 14400 seconds
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasExpirationDuration", "14401"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  void createSASTokenCustomExpirationSuccess() throws Exception {
    azureConfiguration.setSasTokenStartTimeMinutesOffset(5L); // 5 minutes before
    azureConfiguration.setSasTokenExpiryTimeMinutesOffset(10L); // 10 minutes after
    azureConfiguration.setSasTokenExpiryTimeMaximumMinutesOffset(
        240L); // maximum of 240 minutes (4 hours)

    // Specify custom expiration duration of 2 hours.
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasExpirationDuration", "7200"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    // Use expiration time as set in configuration.
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    Mockito.verify(mockAzureStorageAccessService(), times(2))
        .createAzureStorageContainerSasToken(
            eq(workspaceId), eq(storageContainerId), any(), sasTokenOptionsCaptor.capture());

    // First call uses custom time of 2 hours (plus 5 minutes before) = 7500 seconds.
    assertEquals(
        7500,
        sasTokenOptionsCaptor.getAllValues().get(0).expiryTime().toEpochSecond()
            - sasTokenOptionsCaptor.getAllValues().get(0).startTime().toEpochSecond());

    // Second call based on configuration, difference should be 15 minutes = 900 seconds.
    assertEquals(
        900,
        sasTokenOptionsCaptor.getAllValues().get(1).expiryTime().toEpochSecond()
            - sasTokenOptionsCaptor.getAllValues().get(1).startTime().toEpochSecond());
  }

  @Test
  void createSASToken400BadIPRange() throws Exception {
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasIpRange", "not_an_IP"),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  void createSASTokenIpRangeSuccess() throws Exception {
    String ipRange = "168.1.5.60-168.1.5.70";
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasIpRange", ipRange),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    Mockito.verify(mockAzureStorageAccessService())
        .createAzureStorageContainerSasToken(
            eq(workspaceId), eq(storageContainerId), any(), sasTokenOptionsCaptor.capture());
    assertEquals(ipRange, sasTokenOptionsCaptor.getValue().ipRange());
  }

  @Test
  void createSASTokenBlobNameSuccess() throws Exception {
    String blobName = "testing/foo/bar";

    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasBlobName", blobName),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    Mockito.verify(mockAzureStorageAccessService())
        .createAzureStorageContainerSasToken(
            eq(workspaceId), eq(storageContainerId), any(), sasTokenOptionsCaptor.capture());
    assertEquals(blobName, sasTokenOptionsCaptor.getValue().blobName());
  }

  @Test
  void createSASTokenBlobNameInvalidName() throws Exception {
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasBlobName", ""),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }

  @Test
  void createSASTokenBlobPermissionsSuccess() throws Exception {
    String permissions = "rwcdt";
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasPermissions", permissions),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_OK));

    Mockito.verify(mockAzureStorageAccessService())
        .createAzureStorageContainerSasToken(
            eq(workspaceId), eq(storageContainerId), any(), sasTokenOptionsCaptor.capture());
    assertEquals(permissions, sasTokenOptionsCaptor.getValue().permissions());
  }

  @Test
  void createSASTokenBlobPermissionsInvalidPerms() throws Exception {
    String permissions = "fi";
    mockMvc
        .perform(
            addAuth(
                post(String.format(
                        CONTROLLED_AZURE_STORAGE_CONTAINER_SAS_TOKEN_PATH_FORMAT,
                        workspaceId,
                        storageContainerId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .queryParam("sasPermissions", permissions),
                USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));
  }
}
