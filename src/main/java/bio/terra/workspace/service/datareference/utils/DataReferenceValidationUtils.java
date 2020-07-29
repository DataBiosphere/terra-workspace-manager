package bio.terra.workspace.service.datareference.utils;

import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceValidationUtils {

  private ObjectMapper objectMapper;
  private DataRepoService dataRepoService;

  public DataReferenceValidationUtils(ObjectMapper objectMapper, DataRepoService dataRepoService) {
    this.objectMapper = objectMapper;
    this.dataRepoService = dataRepoService;
  }

  public String validateReference(
      ReferenceTypeEnum referenceType, String reference, AuthenticatedUserRequest userReq) {

    if (ReferenceTypeEnum.DATA_REPO_SNAPSHOT.equals(referenceType)) {
      validateDataRepoReference(reference, userReq);
      return reference;
    } else {
      throw new InvalidDataReferenceException(
          "Invalid reference type specified. Valid types include: "
              + ReferenceTypeEnum.DATA_REPO_SNAPSHOT.toString());
    }
  }

  private DataRepoSnapshot validateDataRepoReference(
      String reference, AuthenticatedUserRequest userReq) {
    try {
      DataRepoSnapshot ref = objectMapper.readValue(reference, DataRepoSnapshot.class);
      if (!dataRepoService.snapshotExists(ref.getInstanceName(), ref.getSnapshot(), userReq)) {
        throw new InvalidDataReferenceException(
            "The given snapshot could not be found in the Data Repo instance provided."
                + " Verify that your reference was correctly defined and the instance is correct");
      }
      return ref;
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException(
          "Input could not be parsed as a Data Repo snapshot"
              + " reference. The only valid fields are instanceName and snapshot.");
    }
  }
}
