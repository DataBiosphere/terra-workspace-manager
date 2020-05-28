package bio.terra.workspace.integration.common.utils;

import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * In order to have more control over how tests view errors, we'll handle errors in the test client
 * The test client returns one of two possible data objects (either a workspace response object OR an error object)
 * In lieu of handling errors here, we'll handle the different workspace errors where and when appropriate
 * in the test client
 */
public class WorkspaceManagerClientErrorHandler implements ResponseErrorHandler {

  @Override
  public boolean hasError(ClientHttpResponse response) throws IOException {
    return false;
  }

  @Override
  public void handleError(ClientHttpResponse response) throws IOException {}
}
