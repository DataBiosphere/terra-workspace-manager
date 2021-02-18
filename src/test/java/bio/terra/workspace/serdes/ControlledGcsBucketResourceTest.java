package bio.terra.workspace.serdes;

import bio.terra.workspace.common.BaseUnitTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Test Stairway serialization of the ControlledGcsBucketResource class
 */
public class ControlledGcsBucketResourceTest extends BaseUnitTest {

  @Test
  public void testSerialize() {
    final ObjectMapper objectMapper = StairwayMapper.getObjectMapper();
  }
}
