package bio.terra.workspace.serdes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.StairwayMapper;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Test Stairway serialization of the ControlledGcsBucketResource class */
public class ControlledGcsBucketResourceTest extends BaseUnitTest {

  @Test
  public void testValidateThrows() {
    assertThrows(
        MissingRequiredFieldException.class,
        () ->
            ControlledGcsBucketResource.builder()
                .bucketName(ControlledResourceFixtures.uniqueBucketName())
                .common(
                    ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                        .workspaceUuid(null)
                        .build())
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
}
