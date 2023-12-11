package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.cloud.aws.AwsResourceConstants;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import com.google.common.base.Preconditions;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public class ControlledAwsSageMakerNotebookHandler implements WsmResourceHandler {

  private static ControlledAwsSageMakerNotebookHandler theHandler;

  public static ControlledAwsSageMakerNotebookHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsSageMakerNotebookHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsSageMakerNotebookAttributes attributes =
        DbSerDes.fromJson(
            dbResource.getAttributes(), ControlledAwsSageMakerNotebookAttributes.class);

    return new ControlledAwsSageMakerNotebookResource(
        dbResource, attributes.getInstanceName(), attributes.getInstanceType());
  }

  /**
   * Generate controlled AWS SageMaker Notebook cloud name that meets the requirements for a valid
   * name.
   *
   * <p>Alphanumeric characters and dashes can be safely used in valid names. Dashes may not be
   * first or last characters. For details, see
   * https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_CreateNotebookInstance.html#sagemaker-CreateNotebookInstance-request-NotebookInstanceName
   */
  @Override
  public String generateCloudName(@Nullable String workspaceUserFacingId, String notebookName) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(workspaceUserFacingId));
    String generatedName = notebookName + "-" + workspaceUserFacingId;

    generatedName = generatedName.replaceAll("(\\_)", "-");
    generatedName = generatedName.replaceAll("[^\\-a-zA-Z0-9]+", "");
    generatedName = StringUtils.stripStart(generatedName, "-");

    // truncate before stripEnd, ensuring final name does not end with '-'
    generatedName =
        StringUtils.truncate(
            generatedName, AwsResourceConstants.MAX_SAGEMAKER_NOTEBOOK_INSTANCE_NAME_LENGTH);
    generatedName = StringUtils.stripEnd(generatedName, "-");

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid SageMaker Notebook name from %s, it must contain"
                  + " alphanumerical characters.",
              notebookName));
    }
    return generatedName;
  }
}
