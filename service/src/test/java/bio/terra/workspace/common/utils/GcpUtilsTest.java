package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.BaseUnitTest;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.rpc.Code;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class GcpUtilsTest extends BaseUnitTest {

  @Mock private OperationCow<Operation> mockOperationCow;
  @Mock private OperationCow.OperationAdapter<Operation> mockOperationAdapter;
  @Mock private OperationCow.OperationAdapter.StatusAdapter mockOperationStatusAdapter;

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
    
  // The permission denied code includes cases of the "quota exceeded limit" error for AI notebook
  // creation.
  @Test
  public void pollAndRetry_catchesPermissionDeniedError_forOperation() {
    // Mock the OperationCow.
    when(mockOperationCow.getOperationAdapter()).thenReturn(mockOperationAdapter);
    when(mockOperationAdapter.getDone()).thenReturn(true);

    // Mock the permission error thrown when polling the error (e.g., quota exceeded limit)
    when(mockOperationAdapter.getError()).thenReturn(mockOperationStatusAdapter);
    when(mockOperationStatusAdapter.getCode()).thenReturn(Code.PERMISSION_DENIED_VALUE);
    assertThrows(
        ForbiddenException.class,
        () ->
            GcpUtils.pollAndRetry(mockOperationCow, Duration.ofSeconds(5), Duration.ofMinutes(1)));
  }
}
