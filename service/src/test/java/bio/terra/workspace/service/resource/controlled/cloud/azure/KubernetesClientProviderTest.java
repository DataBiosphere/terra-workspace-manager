package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import io.kubernetes.client.openapi.ApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class KubernetesClientProviderTest {
  @Test
  void testConvertApiExceptionNoStatusCode() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result = provider.convertApiException(new ApiException("message"));

    assertThat(result.map(Object::getClass).orElseThrow(), equalTo(RuntimeException.class));
  }

  @Test
  void testConvertApiExceptionUnknownStatusCode() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result = provider.convertApiException(new ApiException(3000, "message"));

    assertThat(result.map(Object::getClass).orElseThrow(), equalTo(RuntimeException.class));
  }

  @Test
  void testConvertApiExceptionOkStatusCode() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result =
        provider.convertApiException(
            new ApiException(HttpStatus.NOT_FOUND.value(), "message"), HttpStatus.NOT_FOUND);

    assertThat(result.stream().toList(), empty());
  }

  @Test
  void testConvertApiExceptionNotOkStatusCode() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result =
        provider.convertApiException(new ApiException(HttpStatus.BAD_REQUEST.value(), "message"));

    assertThat(result.map(Object::getClass).orElseThrow(), equalTo(RuntimeException.class));
  }

  @Test
  void testConvertApiExceptionRetryable() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result =
        provider.convertApiException(
            new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "message"));

    assertThat(result.map(Object::getClass).orElseThrow(), equalTo(RetryException.class));
  }

  @Test
  void testStepResultFromExceptionRetry() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result =
        provider.stepResultFromException(
            new ApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "message"));

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  @Test
  void testStepResultFromExceptionFatal() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result =
        provider.stepResultFromException(new ApiException(HttpStatus.NOT_FOUND.value(), "message"));

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testStepResultFromExceptionOk() {
    var provider = new KubernetesClientProvider(null, null, null, null, null);
    var result =
        provider.stepResultFromException(
            new ApiException(HttpStatus.NOT_FOUND.value(), "message"), HttpStatus.NOT_FOUND);

    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }
}
