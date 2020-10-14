package bio.terra.workspace.app.configuration.spring;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

import bio.terra.workspace.app.configuration.external.TracingConfiguration;
import com.google.auth.oauth2.ServiceAccountCredentials;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hashids.Hashids;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty("workspace.tracing.enabled")
public class ApiResourceConfig implements WebMvcConfigurer {

  public static final String MDC_REQUEST_ID_HEADER = "X-Request-ID";
  public static final String MDC_REQUEST_ID_KEY = "requestId";
  public static final String MDC_CORRELATION_ID_HEADER = "X-Correlation-ID";

  private final Hashids hashids = new Hashids("requestIdSalt", 8);

  @Autowired
  public ApiResourceConfig(TracingConfiguration tracingConfiguration, Environment environment) {
    try {
      StackdriverTraceExporter.createAndRegister(
          StackdriverTraceConfiguration.builder()
              .setProjectId(tracingConfiguration.getProjectId())
              .setCredentials(
                  ServiceAccountCredentials.fromStream(
                      new FileInputStream(tracingConfiguration.getSaPath())))
              .setFixedAttributes(
                  Map.of(
                      "/component",
                      AttributeValue.stringAttributeValue(
                          environment.getRequiredProperty("spring.application.name"))))
              .build());
    } catch (IOException e) {
      e.printStackTrace();
    }

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
              HttpServletRequest httpRequest, HttpServletResponse httpResponse, Object handler)
              throws Exception {

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
                            ((HandlerMethod) handler)
                                .getMethodAnnotation(RequestMapping.class)
                                .path()[0])));

            return true;
          }
        });
  }

  private String generateRequestId() {
    long generatedLong = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);

    return hashids.encode(generatedLong);
  }

  private String getMDCRequestId(HttpServletRequest httpRequest) {
    return firstNonNull(
        httpRequest.getHeader(MDC_REQUEST_ID_HEADER),
        httpRequest.getHeader(MDC_CORRELATION_ID_HEADER),
        generateRequestId());
  }
}
