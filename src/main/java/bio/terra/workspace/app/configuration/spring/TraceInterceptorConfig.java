package bio.terra.workspace.app.configuration.spring;

import static org.apache.commons.lang3.ObjectUtils.getFirstNonNull;

import bio.terra.workspace.app.configuration.external.TracingConfiguration;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hashids.Hashids;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty("workspace.tracing.enabled")
public class TraceInterceptorConfig implements WebMvcConfigurer {

  public static final String MDC_REQUEST_ID_HEADER = "X-Request-ID";
  public static final String MDC_REQUEST_ID_KEY = "requestId";
  public static final String MDC_CORRELATION_ID_HEADER = "X-Correlation-ID";

  private final Hashids hashids = new Hashids("requestIdSalt", 8);

  @Autowired
  public TraceInterceptorConfig(TracingConfiguration tracingConfiguration) {

    TraceConfig globalTraceConfig = Tracing.getTraceConfig();
    globalTraceConfig.updateActiveTraceParams(
        globalTraceConfig.getActiveTraceParams().toBuilder()
            .setSampler(Samplers.probabilitySampler(tracingConfiguration.getProbability()))
            .build());
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest httpRequest, HttpServletResponse httpResponse, Object handler) {

            // We don't need to do this for resources (swagger ui)
            if (handler instanceof HandlerMethod) {
              // get an mdc id from the request (if not found, create one), and pass it along in the
              // response
              String requestId = getMDCRequestId(httpRequest);
              MDC.put(MDC_REQUEST_ID_KEY, requestId);
              httpResponse.addHeader(MDC_REQUEST_ID_HEADER, requestId);

              // add tags to Stackdriver traces
              Tracing.getTracer()
                  .getCurrentSpan()
                  .putAttributes(
                      Map.of(
                          MDC_REQUEST_ID_KEY,
                          AttributeValue.stringAttributeValue(requestId),
                          "route",
                          AttributeValue.stringAttributeValue(
                              Arrays.stream(
                                      ((HandlerMethod) handler)
                                          .getMethodAnnotation(RequestMapping.class)
                                          .path())
                                  .findFirst()
                                  .orElse("unknown"))));
            }

            return true;
          }
        });
  }

  private String generateRequestId() {
    long generatedLong = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);

    return hashids.encode(generatedLong);
  }

  private String getMDCRequestId(HttpServletRequest httpRequest) {
    return getFirstNonNull(
        () -> httpRequest.getHeader(MDC_REQUEST_ID_HEADER),
        () -> httpRequest.getHeader(MDC_CORRELATION_ID_HEADER),
        this::generateRequestId);
  }
}
