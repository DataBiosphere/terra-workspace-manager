package bio.terra.workspace.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public abstract class BaseMockitoStrictStubbingTest {
  protected MockitoSession mockito;

  @BeforeEach
  public void setup() {
    // initialize session to start mocking
    mockito =
        Mockito.mockitoSession().initMocks(this).strictness(Strictness.STRICT_STUBS).startMocking();
  }

  @AfterEach
  public void tearDown() {
    mockito.finishMocking();
  }
}
