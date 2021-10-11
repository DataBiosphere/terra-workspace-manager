package bio.terra.workspace.app.configuration.external;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WSM Applications are clients of WSM that create a special class of resources: application-owned
 * resources. Being a configured WSM application gives the client control over the lifecycle and
 * configuration of their resources. Workspace users do not have control the way they would for
 * user-created resources. WSM application in this context refers to some piece of middleware that
 * may or may not interact with what a user would call an application.
 */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.application")
public class WsmApplicationConfiguration {
  public static class App {
    private String identifier;
    private String name;
    private String description;
    private String serviceAccount;
    private String state;

    public String getIdentifier() {
      return identifier;
    }

    public void setIdentifier(String identifier) {
      this.identifier = identifier;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getServiceAccount() {
      return serviceAccount;
    }

    public void setServiceAccount(String serviceAccount) {
      this.serviceAccount = serviceAccount;
    }

    public String getState() {
      return state;
    }

    public void setState(String state) {
      this.state = state;
    }
  }

  List<App> configurations;

  public List<App> getConfigurations() {
    return Optional.ofNullable(configurations).orElse(Collections.emptyList());
  }

  public void setConfigurations(List<App> configurations) {
    this.configurations = configurations;
  }
}
