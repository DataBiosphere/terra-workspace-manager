package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  public static final String DEFAULT_GCP_RESOURCE_REGION = "us-central1";

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
