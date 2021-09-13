package bio.terra.workspace.app.configuration.external;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!azure")
public class AzureStateDisabled implements AzureState {
  @Override
  public boolean isEnabled() {
    return false;
  }
}
