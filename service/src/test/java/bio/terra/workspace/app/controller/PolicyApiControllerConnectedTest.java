package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.POLICY_V1_GET_REGION_INFO_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.generated.model.ApiWsmPolicyRegion;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    ApiWsmPolicyRegion region = getRegionInfo("gcp");

    assertEquals("global", region.getName());
    assertEquals("Global", region.getDescription());
    assertFalse(region.getRegions().isEmpty());

    String europeLocation = "Europe";
    ApiWsmPolicyRegion europeRegion =
        region.getRegions().stream()
            .filter(subRegion -> europeLocation.equalsIgnoreCase(subRegion.getName()))
            .findAny()
            .get();

    assertEquals(europeRegion, getRegionInfo("gcp", europeLocation));
  }

  @Test
  public void listRegionDataCenters_azure() throws Exception {
    ApiWsmPolicyRegion region = getRegionInfo("azure");

    assertEquals("global", region.getName());
    assertEquals("Global", region.getDescription());
    assertFalse(region.getRegions().isEmpty());

    String europeLocation = "Europe";
    ApiWsmPolicyRegion europeRegion =
        region.getRegions().stream()
            .filter(subRegion -> europeLocation.equalsIgnoreCase(subRegion.getName()))
            .findAny()
            .get();

    assertEquals(europeRegion, getRegionInfo("azure", europeLocation));
  }

  @Test
  public void listRegionDataCenters_invalidRegion_404() throws Exception {
    getRegionInfoExpect(/*platform=*/ "gcp", /*location=*/ "invalid", HttpStatus.SC_NOT_FOUND);
  }

  private ApiWsmPolicyRegion getRegionInfo(String platform) throws Exception {
    return getRegionInfo(platform, /*location=*/ null);
  }

  private ApiWsmPolicyRegion getRegionInfo(String platform, String location) throws Exception {
    var serializedResponse =
        getRegionInfoExpect(platform, location, HttpStatus.SC_OK)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWsmPolicyRegion.class);
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
