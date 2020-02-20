package bio.terra.workspace.service.create;

// import bio.terra.workspace.service.ping.exception.BadPingException;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CreateService {

  // public String computePing(String message) {
  //   if (StringUtils.isEmpty(message)) {
  //     throw new BadPingException("No message to ping");
  //   }
  //   return "pong: " + message + "\n";
  // }

  public CreatedWorkspace createWorkspace() {
    CreatedWorkspace workspace = new CreatedWorkspace();
    workspace.setId("0");
    return workspace;
  }


}