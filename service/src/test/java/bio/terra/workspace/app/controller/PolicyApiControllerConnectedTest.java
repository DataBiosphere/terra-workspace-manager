package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.POLICY_V1_GET_REGION_INFO_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiWsmPolicyLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TestInstance(Lifecycle.PER_CLASS)
public class PolicyApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  public void listRegionDataCenters_gcp() throws Exception {
    ApiWsmPolicyLocation region = getLocationInfo(ApiCloudPlatform.GCP.name());

    assertEquals("global", region.getName());
    assertEquals("Global Region", region.getDescription());
    assertTrue(region.getRegions().isEmpty());

    String europeLocationName = "europe";
    ApiWsmPolicyLocation europeLocation = getSubLocationRecursive(region, europeLocationName);

    assertEquals(europeLocation, getLocationInfo(ApiCloudPlatform.GCP.name(), europeLocationName));
  }

  @Test
  public void listRegionRegions_azure() throws Exception {
    ApiWsmPolicyLocation region = getLocationInfo(ApiCloudPlatform.AZURE.name());

    assertEquals("global", region.getName());
    assertEquals("Global Region", region.getDescription());
    assertTrue(region.getRegions().isEmpty());

    String usaLocation = "usa";
    ApiWsmPolicyLocation usa = getSubLocationRecursive(region, usaLocation);

    assertEquals(usa, getLocationInfo(ApiCloudPlatform.AZURE.name(), usaLocation));
  }

  private @Nullable ApiWsmPolicyLocation getSubLocationRecursive(
      ApiWsmPolicyLocation location, String name) {
    if (location.getName().equals(name)) {
      return location;
    }
    Queue<ApiWsmPolicyLocation> subLocations = new ArrayDeque<>(location.getSublocations());
    while (!subLocations.isEmpty()) {
      var subLocation = subLocations.poll();
      if (subLocation.getName().equals(name)) {
        return subLocation;
      } else if (subLocation.getSublocations() != null) {
        subLocations.addAll(subLocation.getSublocations());
      }
    }
    return null;
  }

  @Test
  public void listRegionDataCenters_invalidRegion_404() throws Exception {
    getRegionInfoExpect(
        ApiCloudPlatform.GCP.name(), /*location=*/ "invalid", HttpStatus.SC_NOT_FOUND);
  }

  private ApiWsmPolicyLocation getLocationInfo(String platform) throws Exception {
    return getLocationInfo(platform, /*location=*/ null);
  }

  private ApiWsmPolicyLocation getLocationInfo(String platform, String location) throws Exception {
    var serializedResponse =
        getRegionInfoExpect(platform, location, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyLocation.class);
  }

  private ResultActions getRegionInfoExpect(String platform, String region, int status)
      throws Exception {
    return mockMvc
        .perform(
            addAuth(
                get(POLICY_V1_GET_REGION_INFO_PATH)
                    .queryParam("platform", platform)
                    .queryParam("location", region),
                USER_REQUEST))
        .andExpect(status().is(status));
  }
}
