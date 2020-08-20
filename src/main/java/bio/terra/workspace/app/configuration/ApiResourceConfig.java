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

  public static final String MDC_REQUEST_ID_HEADER = "X-Request-ID";
  public static final String MDC_REQUEST_ID_KEY = "requestId";
  public static final String MDC_CORRELATION_ID_HEADER = "X-Correlation-ID";

  @Autowired private SpanCustomizer spanCustomizer;
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
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            httpResponse.addHeader(MDC_REQUEST_ID_HEADER, requestId);

            // add tags to Stackdriver traces
            spanCustomizer.tag(MDC_REQUEST_ID_KEY, requestId);

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
        ((requestId = httpRequest.getHeader(MDC_REQUEST_ID_HEADER)) != null)
            ? requestId
            : ((requestId = httpRequest.getHeader(MDC_CORRELATION_ID_HEADER)) != null)
                ? requestId
                : generateRequestId();
    return requestId;
  }
}
