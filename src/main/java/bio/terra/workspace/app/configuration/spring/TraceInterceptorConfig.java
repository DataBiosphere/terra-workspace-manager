package bio.terra.workspace.app.configuration.spring;

import bio.terra.workspace.app.configuration.external.TracingConfiguration;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.util.Arrays;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** A Spring interceptor that enhances the current tracing span with some additional attributes. */
@Configuration
@ConditionalOnProperty("workspace.tracing.enabled")
public class TraceInterceptorConfig implements WebMvcConfigurer {

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
            Tracing.getTracer()
                .getCurrentSpan()
                .putAttributes(
                    Map.of(
                        "requestId",
                        AttributeValue.stringAttributeValue(MDC.get("requestId")),
                        "route",
                        AttributeValue.stringAttributeValue(
                            Arrays.stream(
                                    ((HandlerMethod) handler)
                                        .getMethodAnnotation(RequestMapping.class)
                                        .path())
                                .findFirst()
                                .orElse("unknown"))));
            return true;
          }
        });
  }
}
