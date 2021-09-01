package bio.terra.workspace.service.azurecompute.exceptions;

public class DeploymentFailedException extends Exception {

  public DeploymentFailedException(String message) {
    super(message);
  }
}
