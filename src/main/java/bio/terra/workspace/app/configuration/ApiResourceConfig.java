package bio.terra.workspace.app.configuration;

import brave.SpanCustomizer;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hashids.Hashids;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiResourceConfig implements WebMvcConfigurer {

  @Autowired private SpanCustomizer spanCustomizer;
  private String mdcRequestIdKey = "X-Request-ID";
  private String mdcCorrelationIdKey = "X-Correlation-ID";
  private Hashids hashids = new Hashids("requestIdSalt", 8);

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
            MDC.put("requestId", requestId);
            httpResponse.addHeader(mdcRequestIdKey, requestId);

            // add tags to Stackdriver traces
            spanCustomizer.tag("requestId", requestId);

            return true;
          }
        });
  }

  private String generateRequestId() {
    long generatedLong = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);

    return hashids.encode(generatedLong);
  }

  private String getMDCRequestId(HttpServletRequest httpRequest) {
    String requestId =
        ((requestId = httpRequest.getHeader(mdcRequestIdKey)) != null)
            ? requestId
            : ((requestId = httpRequest.getHeader(mdcCorrelationIdKey)) != null)
                ? requestId
                : generateRequestId();
    return requestId;
  }
}
