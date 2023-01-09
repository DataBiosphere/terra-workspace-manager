package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.POLICY_V1_GET_REGION_DATA_CENTERS;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.generated.model.ApiDataCenterList;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(Lifecycle.PER_CLASS)
public class PolicyApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  public void listRegionDataCenters() throws Exception {
    ApiDataCenterList list = listRegionDataCenters("gcp");

    assertFalse(list.isEmpty());
  }

  @Test
  public void listRegionDataCenters_azure() throws Exception {
    ApiDataCenterList list = listRegionDataCenters("azure");

    assertFalse(list.isEmpty());
  }

  private ApiDataCenterList listRegionDataCenters(String platform) throws Exception {
    var serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    get(POLICY_V1_GET_REGION_DATA_CENTERS).queryParam("platform", platform),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiDataCenterList.class);
  }
}
