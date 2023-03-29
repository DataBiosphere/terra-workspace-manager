package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  public void makeZoneFromRegion() {
    assertEquals("us-east1-a", GcpUtils.makeZoneFromRegion("us-east1"));
    assertNull(GcpUtils.makeZoneFromRegion(null));
  }

  @Test
  public void isZoneFormat() {
    assertTrue(GcpUtils.isZoneFormat("us-east1-a"));
    assertFalse(GcpUtils.isZoneFormat("us-east1"));
    assertFalse(GcpUtils.isZoneFormat(null));
  }

  @Test
  public void convertLocationToZone() {
    assertEquals("us-east1-a", GcpUtils.convertLocationToZone("us-east1"));
    assertEquals("us-east1-b", GcpUtils.convertLocationToZone("us-east1-b"));
    assertNull(GcpUtils.convertLocationToZone("invalidString"));
    assertNull(GcpUtils.convertLocationToZone(null));
    assertNull(GcpUtils.convertLocationToZone(""));
  }
}
