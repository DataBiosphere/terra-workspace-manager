package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.exception.DatasetNotFoundException;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.Dataset.Access;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatasetCloneRolesComponent {
  private final CrlService crlService;

  @Autowired
  public DatasetCloneRolesComponent(CrlService crlService) {
    this.crlService = crlService;
  }

  public void addDatasetRoles(
      DatasetCloneInputs inputs, String saEmail, AuthenticatedUserRequest userRequest) {
    addOrRemoveAccess(PolicyIdentityOperation.ADD, inputs, saEmail, userRequest);
  }

  public void removeDatasetRoles(
      DatasetCloneInputs inputs,
      String transferServiceSAEmail,
      AuthenticatedUserRequest userRequest) {
    addOrRemoveAccess(PolicyIdentityOperation.REMOVE, inputs, transferServiceSAEmail, userRequest);
  }

  private enum PolicyIdentityOperation {
    ADD,
    REMOVE
  }

  /**
   * Add or remove roles for an Identity.
   *
   * @param operation - flag for add or remove
   * @param inputs - source or destination input object
   * @param saEmail - STS SA email address
   */
  private void addOrRemoveAccess(
      PolicyIdentityOperation operation,
      DatasetCloneInputs inputs,
      String saEmail,
      AuthenticatedUserRequest userRequest) {
    if (inputs.getRoleNames().isEmpty()) {
      // No-op
      return;
    }
    //    final BigQueryCow bigQueryCow = crlService.createBigQueryCow(userRequest);
    final BigQueryCow bigQueryCow = crlService.createWsmSaBigQueryCow();
    try {
      final Dataset dataset =
          bigQueryCow.datasets().get(inputs.getProjectId(), inputs.getDatasetName()).execute();
      final List<Access> existingAccessList = dataset.getAccess();
      final List<Access> deltaAccess =
          inputs.getRoleNames().stream()
              .map(r -> new Access().setIamMember(saEmail).setRole(r))
              .collect(Collectors.toList());
      final List<Access> resolvedAccess;
      switch (operation) {
        case ADD:
          resolvedAccess =
              Stream.concat(existingAccessList.stream(), deltaAccess.stream())
                  .collect(Collectors.toList());
          break;
        case REMOVE:
          resolvedAccess =
              existingAccessList.stream()
                  .filter(a -> !deltaAccess.contains(a)) // O(M*N), but N is max 3
                  .collect(Collectors.toList());
          break;
        default:
          throw new InternalLogicException(
              String.format("Operation %s not recognized.", operation));
      }
      dataset.setAccess(resolvedAccess);
    } catch (IOException e) {
      throw new DatasetNotFoundException(
          String.format(
              "Can't find dataset at projectId %s datasetId %s",
              inputs.getProjectId(), inputs.getDatasetName()),
          e);
    }
  }
}
