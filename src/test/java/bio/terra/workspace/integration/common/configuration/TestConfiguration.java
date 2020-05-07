package bio.terra.workspace.integration.common.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "it")
public class TestConfiguration {

  private String vaultAddress;
  private String vaultPath;
  private String createWorkspaceUrlDev;
  private String serviceAccountEmail;
  private String vaultTokenFileName;

  public String getVaultAddress() {
    return vaultAddress;
  }

  public void setVaultAddress(String vaultAddress) {
    this.vaultAddress = vaultAddress;
  }

  public String getVaultPath() {
    return vaultPath;
  }

  public void setVaultPath(String vaultPath) {
    this.vaultPath = vaultPath;
  }

  public String getCreateWorkspaceUrlDev() {
    return createWorkspaceUrlDev;
  }

  public void setCreateWorkspaceUrlDev(String createWorkspaceUrlDev) {
    this.createWorkspaceUrlDev = createWorkspaceUrlDev;
  }

  public String getServiceAccountEmail() {
    return serviceAccountEmail;
  }

  public void setServiceAccountEmail(String serviceAccountEmail) {
    this.serviceAccountEmail = serviceAccountEmail;
  }

  public String getVaultTokenFileName() {
    return vaultTokenFileName;
  }

  public void setVaultTokenFileName(String vaultTokenFileName) {
    this.vaultTokenFileName = vaultTokenFileName;
  }
}
