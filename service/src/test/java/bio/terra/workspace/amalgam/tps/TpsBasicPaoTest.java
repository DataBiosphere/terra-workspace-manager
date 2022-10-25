package bio.terra.workspace.amalgam.tps;

import static bio.terra.workspace.common.fixtures.PolicyFixtures.DDGROUP;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.GROUP;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.GROUP_CONSTRAINT;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.GROUP_POLICY;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.NAMESPACE;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.REGION;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.REGION_CONSTRAINT;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.REGION_POLICY;
import static bio.terra.workspace.common.fixtures.PolicyFixtures.US_REGION;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPaoReplaceRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoSourceRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsUpdateMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public class TpsBasicPaoTest extends BaseUnitTest {
  private static final Logger logger = LoggerFactory.getLogger(TpsBasicPaoTest.class);

  @Autowired private ObjectMapper objectMapper;
  @Autowired private FeatureConfiguration features;
  @Autowired private MockMvc mockMvc;

  @Test
  public void basicPaoTest() throws Exception {
    // Don't run the test if TPS is disabled
    logger.info("features.isTpsEnabled(): %s".formatted(features.isTpsEnabled()));
    if (!features.isTpsEnabled()) {
      return;
    }

    var inputs = new ApiTpsPolicyInputs().addInputsItem(GROUP_POLICY).addInputsItem(REGION_POLICY);

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
    assertTrue(updateResult.isUpdateApplied());
    assertEquals(0, updateResult.getConflicts().size());
    checkAttributeSet(updateResult.getResultingPao().getEffectiveAttributes());

    // Link a PAO
    updateResult = connectPao(paoIdB, paoIdA, "link");
    assertTrue(updateResult.isUpdateApplied());
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

    // Replace a PAO
    var replaceRequest =
        new ApiTpsPaoReplaceRequest()
            .updateMode(ApiTpsUpdateMode.FAIL_ON_CONFLICT)
            .newAttributes(inputs);
    var replaceJson = objectMapper.writeValueAsString(replaceRequest);
    result =
        mockMvc
            .perform(
                addAuth(
                    addJsonContentType(
                        put("/api/policy/v1alpha1/pao/" + paoIdB).content(replaceJson)),
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
      assertEquals(NAMESPACE, attribute.getNamespace());
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
