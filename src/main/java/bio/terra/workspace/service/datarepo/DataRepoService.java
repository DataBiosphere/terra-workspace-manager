package bio.terra.workspace.service.datarepo;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class DataRepoService {

  private RestTemplate restTemplate;

  @Bean
  private RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }

  public DataRepoService() {
    this.restTemplate = new RestTemplate();
  }

  private String getSnapshotUrl(String instance, String snapshotId) {
    return instance + "/api/repository/v1/snapshots/" + snapshotId;
  }

  public boolean snapshotExists(
      String instance, String snapshotId, AuthenticatedUserRequest userReq) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(userReq.getRequiredToken());

      HttpEntity<String> entity = new HttpEntity<>(headers);
      ResponseEntity response =
          restTemplate.exchange(
              getSnapshotUrl(instance, snapshotId), HttpMethod.GET, entity, Object.class);

      return response.getStatusCode().is2xxSuccessful();
    } catch (RestClientException e) {
      return false;
    }
  }
}
