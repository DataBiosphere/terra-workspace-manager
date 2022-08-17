package bio.terra.workspace.serdes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.StairwayMapper;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Test Stairway serialization of the ControlledGcsBucketResource class */
public class ControlledGcsBucketResourceTest extends BaseUnitTest {

  @Test
  public void testValidateThrows() {
    assertThrows(
        MissingRequiredFieldException.class,
        () ->
            ControlledGcsBucketResource.builder()
                .common(
                    ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                        .workspaceUuid(null)
                        .build())
                .bucketName(ControlledResourceFixtures.uniqueBucketName())
                .build());
  }

  @Test
  public void testSerialization() throws JsonProcessingException {
    ControlledGcsBucketResource gcsBucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(null).build();

    final ObjectMapper objectMapper = StairwayMapper.getObjectMapper();
    final String serialized = objectMapper.writeValueAsString(gcsBucketResource);

    final ControlledGcsBucketResource deserialized =
        objectMapper.readValue(serialized, ControlledGcsBucketResource.class);

    assertThat(deserialized, equalTo(gcsBucketResource));
  }

  @Test
  public void testCloningInstructionsValidation() {
    assertThrows(
        BadRequestException.class,
        () ->
            ControlledGcsBucketResource.builder()
                .common(
                    ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                        .workspaceUuid(UUID.randomUUID())
                        .resourceId(UUID.randomUUID())
                        .name("controlled_bucket_1")
                        .description(
                            "how much data could a dataset set if a dataset could set data?")
                        .cloningInstructions(CloningInstructions.COPY_REFERENCE) // not valid (yet!)
                        .assignedUser(null)
                        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                        .managedBy(ManagedByType.MANAGED_BY_USER)
                        .build())
                .bucketName(ControlledResourceFixtures.uniqueBucketName())
                .build());
  }
}
