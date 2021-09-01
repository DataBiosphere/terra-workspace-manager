package bio.terra.workspace.service.azurecompute.model;

public class CreateDeploymentRequest {
  public String template;
  public String deploymentName;

  public CreateDeploymentRequest(String template, String deploymentName) {
    this.template = template;
    this.deploymentName = deploymentName;
  }
}
