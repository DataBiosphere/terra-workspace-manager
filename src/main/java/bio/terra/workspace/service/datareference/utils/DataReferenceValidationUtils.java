package bio.terra.workspace.service.datareference.utils;

import static bio.terra.workspace.generated.model.ReferenceTypeEnum.DATA_REPO_SNAPSHOT;

import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceValidationUtils {

  private DataRepoService dataRepoService;
  public static final Pattern NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][_a-zA-Z0-9]{0,62}$");

  public static void validateReferenceName(String name) {
    if (StringUtils.isEmpty(name) || !NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      throw new InvalidDataReferenceException(
          "Invalid reference name specified. Name must be 1 to 63 alphanumeric characters or underscores, and cannot start with an underscore.");
    }
  }

  public DataReferenceValidationUtils(DataRepoService dataRepoService) {
    this.dataRepoService = dataRepoService;
  }

  public void validateReferenceObject(
      DataReferenceType referenceType,
      ReferenceObject reference,
      AuthenticatedUserRequest userReq) {

    switch (referenceType) {
      case DATA_REPO_SNAPSHOT:
        validateSnapshotReference((SnapshotReference) reference, userReq);
      default:
        throw new InvalidDataReferenceException(
            "Invalid reference type specified. Valid types include: "
                + DATA_REPO_SNAPSHOT.toString());
    }
  }

  private void validateSnapshotReference(SnapshotReference ref, AuthenticatedUserRequest userReq) {
    if (StringUtils.isBlank(ref.getInstanceName()) || StringUtils.isBlank(ref.getSnapshot())) {
      throw new InvalidDataReferenceException(
          "Invalid Data Repo Snapshot identifier: "
              + "instanceName and snapshot must both be provided.");
    }
    if (!dataRepoService.snapshotExists(ref.getInstanceName(), ref.getSnapshot(), userReq)) {
      throw new InvalidDataReferenceException(
          "The given snapshot could not be found in the Data Repo instance provided."
              + " Verify that your reference was correctly defined and the instance is correct");
    }
  }
}
