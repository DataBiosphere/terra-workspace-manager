package bio.terra.workspace.integration.common.utils;

import bio.terra.workspace.integration.common.auth.AuthService;
import bio.terra.workspace.integration.common.response.WorkspaceResponse;
import bio.terra.workspace.model.ErrorReport;
import java.io.IOException;
import java.util.Collections;
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
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
  }

  public <T> WorkspaceResponse<T> post(
      String userEmail, String path, String json, Class<T> responseClass) throws Exception {
    HttpEntity<String> entity = new HttpEntity<>(json, getHeaders(userEmail));
    return sendRequest(path, HttpMethod.POST, entity, ErrorReport.class, responseClass);
  }

  public <T> WorkspaceResponse<T> get(String userEmail, String path, Class<T> responseClass)
      throws Exception {
    HttpEntity<String> entity = new HttpEntity<>(getHeaders(userEmail));
    return sendRequest(path, HttpMethod.GET, entity, ErrorReport.class, responseClass);
  }

  public <T> WorkspaceResponse<T> delete(String userEmail, String path, String json)
      throws Exception {
    HttpEntity<String> entity = new HttpEntity<>(json, getHeaders(userEmail));
    return sendRequest(path, HttpMethod.DELETE, entity, ErrorReport.class, null);
  }

  private HttpHeaders getHeaders(String userEmail) throws IOException {
    HttpHeaders headersCopy = new HttpHeaders(headers);
    headersCopy.setBearerAuth(authService.getAuthToken(userEmail));
    return headersCopy;
  }

  private <S, T> WorkspaceResponse<T> sendRequest(
      String path,
      HttpMethod method,
      HttpEntity<String> entity,
      Class<S> errorClass,
      Class<T> responseClass)
      throws Exception {

    logger.info("api request: method={} path={}", method.toString(), path);
    ResponseEntity<String> response = restTemplate.exchange(path, method, entity, String.class);
    WorkspaceResponse<T> workspaceResponse = new WorkspaceResponse<>();
    workspaceResponse.setStatusCode(response.getStatusCode());
    if (response.getStatusCode().is2xxSuccessful()) {
      if (responseClass != null) {
        T responseObject = testUtils.mapFromJson(response.getBody(), responseClass);
        workspaceResponse.setResponseObject(responseObject);
      }
    } else {
      S errorObject = testUtils.mapFromJson(response.getBody(), errorClass);
      workspaceResponse.setErrorObject((ErrorReport) errorObject);
    }
    return workspaceResponse;
  }
}
