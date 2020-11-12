package bio.terra.workspace.service.status;

import bio.terra.workspace.generated.model.SystemStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fake status service used for tests. Always returns OK status with no subsystems. */
@Component
@Profile("test")
public class FakeStatusService implements StatusService {

  @Override
  public SystemStatus getCurrentStatus() {
    return new SystemStatus().ok(true);
  }
}
