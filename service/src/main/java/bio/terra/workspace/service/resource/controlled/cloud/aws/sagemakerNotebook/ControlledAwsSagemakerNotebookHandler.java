package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

public class ControlledAwsSagemakerNotebookHandler implements WsmResourceHandler {

  private static ControlledAwsSagemakerNotebookHandler theHandler;

  public static ControlledAwsSagemakerNotebookHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsSagemakerNotebookHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsSagemakerNotebookResource attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAwsSagemakerNotebookResource.class);

    return new ControlledAwsSagemakerNotebookResource(
        dbResource, attributes.getInstanceName(), attributes.getInstanceType());
  }

  /**
   * Generate controlled AWS Sagemaker Notebook cloud name that meets the requirements for a valid
   * name.
   *
   * <p>Alphanumeric characters and certain special characters can be safely used in valid names For
   * details, see https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new NotImplementedException("TODO-Dex");
  }
}
