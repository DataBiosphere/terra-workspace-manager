package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

public class GcpUtilsTest extends BaseUnitTest {

  @Test
  public void parseRegionWithRegions() {
    assertEquals("us-east1", GcpUtils.parseRegion("us-east1"));
    assertEquals("asia-northeast3", GcpUtils.parseRegion("asia-northeast3"));
    assertEquals("australia-southeast2", GcpUtils.parseRegion("australia-southeast2"));
  }

  @Test
  public void parseRegionEdgeCases() {
    assertEquals("", GcpUtils.parseRegion(""));
    assertEquals(" ", GcpUtils.parseRegion(" "));
    assertEquals("-a", GcpUtils.parseRegion("-a"));
  }

  @Test
  public void parseRegionWithZones() {
    assertEquals("us-east1", GcpUtils.parseRegion("us-east1-a"));
    assertEquals("asia-northeast3", GcpUtils.parseRegion("asia-northeast3-c"));
    assertEquals("australia-southeast2", GcpUtils.parseRegion("australia-southeast2-b"));
  }

  // The permission denied code includes cases of the "quota exceeded limit" error for AI notebook
  // creation.
  @Test
  public void pollAndRetry_catchesPermissionDeniedError() {

  }
}
