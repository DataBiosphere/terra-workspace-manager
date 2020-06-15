package bio.terra.workspace.service.datareference.utils;

import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
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
      DataReferenceDescription.ReferenceTypeEnum referenceType,
      String reference,
      AuthenticatedUserRequest userReq) {

    if (referenceType.equals(DataReferenceDescription.ReferenceTypeEnum.DATAREPOSNAPSHOT)) {
      validateDataRepoReference(reference, userReq);
      return reference;
    } else {
      throw new InvalidDataReferenceException(
          "Invalid reference type specified: " + referenceType.getValue());
    }
  }

  private DataRepoSnapshot validateDataRepoReference(
      String reference, AuthenticatedUserRequest userReq) {
    try {
      DataRepoSnapshot ref = objectMapper.readValue(reference, DataRepoSnapshot.class);
      if (!dataRepoService.snapshotExists(ref.getInstanceName(), ref.getSnapshot(), userReq)) {
        throw new InvalidDataReferenceException(
            "Snapshot ["
                + ref.getSnapshot()
                + "] could not be found in Data Repo located at ["
                + ref.getInstanceName()
                + "]. Verify that your reference was correctly defined: ["
                + reference
                + "]");
      }
      return ref;
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Invalid DataRepoSnapshot specified: " + reference);
    }
  }
}
