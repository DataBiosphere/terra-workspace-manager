package bio.terra.workspace.amalgam.tps;

import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public class TpsBasicPaoTest extends BaseUnitTest {
  private static final String TERRA = "terra";
  private static final String GROUP_CONSTRAINT = "group-constraint";
  private static final String REGION_CONSTRAINT = "region-constraint";
  private static final String GROUP = "group";
  private static final String REGION = "region";
  private static final String DDGROUP = "ddgroup";
  private static final String US_REGION = "US";
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FeatureConfiguration features;
  @Autowired private MockMvc mockMvc;

  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Test
  public void basicPaoTest() throws Exception {
    // Don't run the test if TPS is disabled
    if (!features.isTpsEnabled()) {
      return;
    }

    // Create a PAO
    var objectId = UUID.randomUUID();

    var groupPolicy =
        new ApiTpsPolicyInput()
            .namespace(TERRA)
            .name(GROUP_CONSTRAINT)
            .addAdditionalDataItem(new ApiTpsPolicyPair().key(GROUP).value(DDGROUP));

    var regionPolicy =
        new ApiTpsPolicyInput()
            .namespace(TERRA)
            .name(REGION_CONSTRAINT)
            .addAdditionalDataItem(new ApiTpsPolicyPair().key(REGION).value(US_REGION));

    var inputs = new ApiTpsPolicyInputs().addInputsItem(groupPolicy).addInputsItem(regionPolicy);

    var apiRequest =
        new ApiTpsPaoCreateRequest()
            .component(ApiTpsComponent.WSM)
            .objectType(ApiTpsObjectType.WORKSPACE)
            .objectId(objectId)
            .attributes(inputs);

    String json = objectMapper.writeValueAsString(apiRequest);

    // Create a PAO
    MvcResult result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(post("/api/policy/v1alpha1/pao").content(json)),
                    USER_REQUEST))
            .andReturn();
    MockHttpServletResponse response = result.getResponse();
    HttpStatus status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.NO_CONTENT, status);

    // Get a PAO
    result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(get("/api/policy/v1alpha1/pao/" + objectId).content(json)),
                    USER_REQUEST))
            .andReturn();
    response = result.getResponse();
    status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.OK, status);

    var apiPao = objectMapper.readValue(response.getContentAsString(), ApiTpsPaoGetResult.class);
    assertEquals(objectId, apiPao.getObjectId());
    assertEquals(ApiTpsComponent.WSM, apiPao.getComponent());
    assertEquals(ApiTpsObjectType.WORKSPACE, apiPao.getObjectType());
    checkAttributeSet(apiPao.getAttributes());
    checkAttributeSet(apiPao.getEffectiveAttributes());

    // Clone a PAO
    var destinationObjectId = UUID.randomUUID();
    var cloneRequest = new ApiTpsPaoCloneRequest().destinationObjectId(destinationObjectId);
    String cloneJson = objectMapper.writeValueAsString(cloneRequest);
    /*
        // Clone request
        result =
            mockMvc
                .perform(
                    addAuth(
                        addJsonContentType(
                            post("/api/policy/v1alpha1/pao/" + objectId).content(cloneJson)),
                        USER_REQUEST))
                .andReturn();
        response = result.getResponse();
        status = HttpStatus.valueOf(response.getStatus());
        assertEquals(HttpStatus.CREATED, status);

        // retrieve the clone
        result =
            mockMvc
                .perform(
                    addAuth(
                        addJsonContentType(get("/api/policy/v1alpha1/pao/" + destinationObjectId)),
                        USER_REQUEST))
                .andReturn();
        response = result.getResponse();
        status = HttpStatus.valueOf(response.getStatus());
        assertEquals(HttpStatus.OK, status);

        // validate clone
        apiPao = objectMapper.readValue(response.getContentAsString(), ApiTpsPaoGetResult.class);
        assertEquals(destinationObjectId, apiPao.getObjectId());
        assertEquals(objectId, apiPao.getPredecessorId());
        assertEquals(ApiTpsComponent.WSM, apiPao.getComponent());
        assertEquals(ApiTpsObjectType.WORKSPACE, apiPao.getObjectType());
        checkAttributeSet(apiPao.getAttributes());
        checkAttributeSet(apiPao.getEffectiveAttributes());
    */
    // Delete a PAO
    result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(
                        delete("/api/policy/v1alpha1/pao/" + objectId).content(json)),
                    USER_REQUEST))
            .andReturn();
    response = result.getResponse();
    status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.NO_CONTENT, status);
  }

  private void checkAttributeSet(ApiTpsPolicyInputs attributeSet) {
    for (ApiTpsPolicyInput attribute : attributeSet.getInputs()) {
      assertEquals(TERRA, attribute.getNamespace());
      assertEquals(1, attribute.getAdditionalData().size());

      if (attribute.getName().equals(GROUP_CONSTRAINT)) {
        assertEquals(GROUP, attribute.getAdditionalData().get(0).getKey());
        assertEquals(DDGROUP, attribute.getAdditionalData().get(0).getValue());
      } else if (attribute.getName().equals(REGION_CONSTRAINT)) {
        assertEquals(REGION, attribute.getAdditionalData().get(0).getKey());
        assertEquals(US_REGION, attribute.getAdditionalData().get(0).getValue());
      } else {
        fail();
      }
    }
  }
}
