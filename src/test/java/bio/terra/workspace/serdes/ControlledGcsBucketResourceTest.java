package bio.terra.workspace.serdes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.stairway.StairwayMapper;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Test Stairway serialization of the ControlledGcsBucketResource class */
public class ControlledGcsBucketResourceTest extends BaseUnitTest {

  @Test
  public void testValidateOk() {
    ControlledGcsBucketResource gcsBucketResource =
        ControlledResourceFixtures.makeControlledGcsBucketResource(UUID.randomUUID());
    // will throw if anything is amiss
    gcsBucketResource.validate();
  }

  @Test
  public void testValidateThrows() {
    assertThrows(
        MissingRequiredFieldException.class,
        () -> ControlledResourceFixtures.makeControlledGcsBucketResource(null));
  }

  @Test
  public void testSerialization() throws JsonProcessingException {
    ControlledGcsBucketResource gcsBucketResource =
        ControlledResourceFixtures.makeControlledGcsBucketResource(UUID.randomUUID());

    final ObjectMapper objectMapper = StairwayMapper.getObjectMapper();
    final String serialized = objectMapper.writeValueAsString(gcsBucketResource);

    final ControlledGcsBucketResource deserialized =
        objectMapper.readValue(serialized, ControlledGcsBucketResource.class);

    assertThat(deserialized, equalTo(gcsBucketResource));
  }
}
