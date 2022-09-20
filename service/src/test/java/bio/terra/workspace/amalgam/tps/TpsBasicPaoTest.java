package bio.terra.workspace.amalgam.tps;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPaoSourceRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import bio.terra.workspace.generated.model.ApiTpsUpdateMode;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class TpsBasicPaoTest extends BaseUnitTest {
  private static final String TERRA = "terra";
  private static final String GROUP_CONSTRAINT = "group-constraint";
  private static final String REGION_CONSTRAINT = "region-constraint";
  private static final String GROUP = "group";
  private static final String REGION = "region";
  private static final String DDGROUP = "ddgroup";
  private static final String US_REGION = "US";
  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FeatureConfiguration features;
  @Autowired private MockMvc mockMvc;

  @Test
  public void basicPaoTest() throws Exception {
    // Don't run the test if TPS is disabled
    if (!features.isTpsEnabled()) {
      return;
    }

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

    // Create a PAO
    UUID paoIdA = createPao(inputs);

    // Create another PAO with no policies
    UUID paoIdB = createPao(new ApiTpsPolicyInputs());

    // Get a PAO
    MvcResult result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(get("/api/policy/v1alpha1/pao/" + paoIdA)), USER_REQUEST))
            .andReturn();
    MockHttpServletResponse response = result.getResponse();
    HttpStatus status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.OK, status);

    var apiPao = objectMapper.readValue(response.getContentAsString(), ApiTpsPaoGetResult.class);
    assertEquals(paoIdA, apiPao.getObjectId());
    assertEquals(ApiTpsComponent.WSM, apiPao.getComponent());
    assertEquals(ApiTpsObjectType.WORKSPACE, apiPao.getObjectType());
    checkAttributeSet(apiPao.getAttributes());
    checkAttributeSet(apiPao.getEffectiveAttributes());

    // Merge a PAO
    var updateResult = connectPao(paoIdB, paoIdA, "merge");
    assertTrue(updateResult.isSucceeded());
    assertEquals(0, updateResult.getConflicts().size());
    checkAttributeSet(updateResult.getResultingPao().getEffectiveAttributes());

    // Link a PAO
    updateResult = connectPao(paoIdB, paoIdA, "link");
    assertTrue(updateResult.isSucceeded());
    assertEquals(0, updateResult.getConflicts().size());
    checkAttributeSet(updateResult.getResultingPao().getEffectiveAttributes());

    // Update a PAO
    var updateRequest =
        new ApiTpsPaoUpdateRequest()
            .updateMode(ApiTpsUpdateMode.FAIL_ON_CONFLICT)
            .addAttributes(inputs);
    var updateJson = objectMapper.writeValueAsString(updateRequest);

    result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(
                        patch("/api/policy/v1alpha1/pao/" + paoIdB).content(updateJson)),
                    USER_REQUEST))
            .andReturn();
    response = result.getResponse();
    status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.OK, status);
    updateResult =
        objectMapper.readValue(response.getContentAsString(), ApiTpsPaoUpdateResult.class);
    checkAttributeSet(updateResult.getResultingPao().getEffectiveAttributes());

    // Delete a PAO
    deletePao(paoIdA);
    deletePao(paoIdB);
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

  private UUID createPao(ApiTpsPolicyInputs inputs) throws Exception {
    UUID objectId = UUID.randomUUID();
    var apiRequest =
        new ApiTpsPaoCreateRequest()
            .component(ApiTpsComponent.WSM)
            .objectType(ApiTpsObjectType.WORKSPACE)
            .objectId(objectId)
            .attributes(inputs);

    String json = objectMapper.writeValueAsString(apiRequest);

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

    return objectId;
  }

  private ApiTpsPaoUpdateResult connectPao(UUID targetId, UUID sourceId, String operation)
      throws Exception {
    var connectRequest =
        new ApiTpsPaoSourceRequest()
            .sourceObjectId(sourceId)
            .updateMode(ApiTpsUpdateMode.FAIL_ON_CONFLICT);
    String connectJson = objectMapper.writeValueAsString(connectRequest);
    String url = String.format("/api/policy/v1alpha1/pao/%s/%s", targetId, operation);

    MvcResult result =
        mockMvc
            .perform(addAuth(addJsonContentType(post(url).content(connectJson)), USER_REQUEST))
            .andReturn();
    MockHttpServletResponse response = result.getResponse();
    HttpStatus status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.OK, status);

    return objectMapper.readValue(response.getContentAsString(), ApiTpsPaoUpdateResult.class);
  }

  private void deletePao(UUID objectId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(delete("/api/policy/v1alpha1/pao/" + objectId)),
                    USER_REQUEST))
            .andReturn();
    MockHttpServletResponse response = result.getResponse();
    HttpStatus status = HttpStatus.valueOf(response.getStatus());
    assertEquals(HttpStatus.NO_CONTENT, status);
  }
}
