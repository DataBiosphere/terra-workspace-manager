package bio.terra.workspace.service.datareference.utils;

import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** A collection of validation functions for data references. */
@Component
public class DataReferenceValidationUtils {

  private DataRepoService dataRepoService;

  /**
   * Names must be 1-63 characters long, and may consist of alphanumeric characters and underscores
   * (but may not start with an underscore). These restrictions match TDR snapshot name
   * restrictions.
   */
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

  /**
   * Validates a referenceObject, with specific validation rules varying based on the actual type of
   * the object.
   */
  public void validateReferenceObject(
      ReferenceObject reference,
      DataReferenceType referenceType,
      AuthenticatedUserRequest userReq) {

    switch (referenceType) {
      case DATA_REPO_SNAPSHOT:
        validateSnapshotReference((SnapshotReference) reference, userReq);
        return;
      default:
        throw new InvalidDataReferenceException(
            "Invalid reference type specified. Valid types include: "
                + DataReferenceType.DATA_REPO_SNAPSHOT.toString());
    }
  }

  private void validateSnapshotReference(SnapshotReference ref, AuthenticatedUserRequest userReq) {
    if (StringUtils.isBlank(ref.instanceName()) || StringUtils.isBlank(ref.snapshot())) {
      throw new InvalidDataReferenceException(
          "Invalid Data Repo Snapshot identifier: "
              + "instanceName and snapshot must both be provided.");
    }
    if (!dataRepoService.snapshotExists(ref.instanceName(), ref.snapshot(), userReq)) {
      throw new InvalidDataReferenceException(
          "The given snapshot could not be found in the Data Repo instance provided."
              + " Verify that your reference was correctly defined and the instance is correct");
    }
  }
}
