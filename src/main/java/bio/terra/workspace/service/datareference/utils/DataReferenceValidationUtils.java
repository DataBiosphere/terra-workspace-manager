package bio.terra.workspace.service.datareference.utils;

import static bio.terra.workspace.generated.model.ReferenceTypeEnum.DATA_REPO_SNAPSHOT;

import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceValidationUtils {

  private DataRepoService dataRepoService;
  private final Pattern nameValidationPattern = Pattern.compile("^[a-zA-Z0-9][_a-zA-Z0-9]{0,62}$");

  public DataReferenceValidationUtils(DataRepoService dataRepoService) {
    this.dataRepoService = dataRepoService;
  }

  public void validateReferenceName(String name) {
    if (StringUtils.isEmpty(name) || !nameValidationPattern.matcher(name).matches()) {
      throw new InvalidDataReferenceException(
          "Invalid reference name specified. Name must be 1 to 63 alphanumeric characters or underscores, and cannot start with an underscore.");
    }
  }

  public DataRepoSnapshot validateReference(
      ReferenceTypeEnum referenceType,
      DataRepoSnapshot reference,
      AuthenticatedUserRequest userReq) {

    switch (referenceType) {
      case DATA_REPO_SNAPSHOT:
        validateDataRepoReference(reference, userReq);
        return reference;
      default:
        throw new InvalidDataReferenceException(
            "Invalid reference type specified. Valid types include: "
                + DATA_REPO_SNAPSHOT.toString());
    }
  }

  private void validateDataRepoReference(DataRepoSnapshot ref, AuthenticatedUserRequest userReq) {
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
