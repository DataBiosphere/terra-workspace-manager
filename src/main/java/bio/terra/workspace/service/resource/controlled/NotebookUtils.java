package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.generated.model.ApiGcsAiNotebookInstanceCreationParameters;
import com.google.api.services.notebooks.v1.model.Instance;

/** Utilitis for working with Google AI Notebooks. */
public class NotebookUtils {
  private NotebookUtils() {}

  /**
   * Sets the parameters on the {@link Instance} that are specified by the {@link
   * ApiGcsAiNotebookInstanceCreationParameters} to the {@link Instance} Google API model
   * representation.
   */
  public static Instance setFields(ApiGcsAiNotebookInstanceCreationParameters parameters) {
    Instance instance = new Instance();
    return instance;
  }
}
