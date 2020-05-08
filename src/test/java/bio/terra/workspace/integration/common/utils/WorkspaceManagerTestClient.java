package bio.terra.workspace.integration.common.utils;

import bio.terra.workspace.generated.model.ErrorReport;
import bio.terra.workspace.integration.common.auth.AuthService;
import bio.terra.workspace.integration.common.response.ObjectOrErrorResponse;
import bio.terra.workspace.integration.common.response.WorkspaceResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WorkspaceManagerTestClient {

  @Autowired private AuthService authService;

  @Autowired private TestUtils testUtils;

  private final HttpHeaders headers;
  private final RestTemplate restTemplate;
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceManagerTestClient.class);

  @Autowired
  public WorkspaceManagerTestClient() {
    restTemplate = new RestTemplate();
    restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    restTemplate.setErrorHandler(new WorkspaceManagerClientErrorHandler());
    headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
  }

  public <T> WorkspaceResponse<T> post(
      String userEmail, String path, String json, Class<T> responseClass) throws Exception {
    HttpEntity<String> entity = new HttpEntity<>(json, getHeaders(userEmail));
    return new WorkspaceResponse<>(
        sendRequest(path, HttpMethod.POST, entity, responseClass, ErrorReport.class));
  }

  private HttpHeaders getHeaders(String userEmail) throws IOException, InterruptedException {
    HttpHeaders headersCopy = new HttpHeaders(headers);
    // Uncomment the line below when we start to validate header access token
    // headersCopy.setBearerAuth(authService.getAuthToken(userEmail));
    return headersCopy;
  }

  private <S, T> ObjectOrErrorResponse<S, T> sendRequest(
      String path,
      HttpMethod method,
      HttpEntity entity,
      Class<T> responseClass,
      Class<S> errorClass)
      throws Exception {

    logger.info("api request: method={} path={}", method.toString(), path);
    ResponseEntity<String> response = restTemplate.exchange(path, method, entity, String.class);
    ObjectOrErrorResponse<S, T> workspaceResponse = new ObjectOrErrorResponse<>();
    workspaceResponse.setStatusCode(response.getStatusCode());

    if (response.getStatusCode().is2xxSuccessful()) {
      if (responseClass != null) {
        T responseObject = testUtils.mapFromJson(response.getBody(), responseClass);
        workspaceResponse.setResponseObject(Optional.of(responseObject));
      } else {
        workspaceResponse.setResponseObject(Optional.empty());
      }
      workspaceResponse.setErrorModel(Optional.empty());
    } else {
      S errorObject = testUtils.mapFromJson(response.getBody(), errorClass);
      workspaceResponse.setErrorModel(Optional.of(errorObject));
      workspaceResponse.setResponseObject(Optional.empty());
    }
    return workspaceResponse;
  }
}
