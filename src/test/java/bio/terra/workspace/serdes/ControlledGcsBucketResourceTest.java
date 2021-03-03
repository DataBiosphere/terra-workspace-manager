package bio.terra.workspace.serdes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.stairway.StairwayMapper;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Test Stairway serialization of the ControlledGcsBucketResource class */
public class ControlledGcsBucketResourceTest extends BaseUnitTest {

  @Test
  public void testValidateOk() {
    // will throw if anything is amiss
    ControlledResourceFixtures.BUCKET_RESOURCE.validate();
  }

  @Test
  public void testValidateThrows() {
    assertThrows(
        IllegalStateException.class, ControlledResourceFixtures.INVALID_BUCKET_RESOURCE::validate);
  }

  @Test
  public void testSerialization() throws JsonProcessingException {
    final ObjectMapper objectMapper = StairwayMapper.getObjectMapper();
    final String serialized =
        objectMapper.writeValueAsString(ControlledResourceFixtures.BUCKET_RESOURCE);

    final ControlledGcsBucketResource deserialized =
        objectMapper.readValue(serialized, ControlledGcsBucketResource.class);

    assertThat(deserialized, equalTo(ControlledResourceFixtures.BUCKET_RESOURCE));
  }
}
