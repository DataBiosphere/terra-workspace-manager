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
      Object reference,
      AuthenticatedUserRequest userReq) {

    if (referenceType.equals(DataReferenceDescription.ReferenceTypeEnum.DATAREPOSNAPSHOT)) {
      try {
        String ref = objectMapper.writeValueAsString(reference);
        validateDataRepoReference(ref, userReq);
        return ref;
      } catch (JsonProcessingException e) {
        throw new InvalidDataReferenceException("Invalid DataRepoSnapshot specified: " + reference);
      }
    } else {
      throw new InvalidDataReferenceException(
          "Invalid reference type specified: " + referenceType.getValue());
    }
  }

  private DataRepoSnapshot validateDataRepoReference(
      String reference, AuthenticatedUserRequest userReq) {
    try {
      DataRepoSnapshot ref = objectMapper.readValue(reference, DataRepoSnapshot.class);
      if (!dataRepoService.snapshotExists(ref.getInstance(), ref.getSnapshot(), userReq)) {
        throw new InvalidDataReferenceException(
            "Snapshot ["
                + ref.getSnapshot()
                + "] could not be found in Data Repo located at ["
                + ref.getInstance()
                + "]");
      }
      return ref;
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Invalid DataRepoSnapshot specified: " + reference);
    }
  }
}
