package bio.terra.workspace.app.configuration;

import brave.SpanCustomizer;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiResourceConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**").addResourceLocations("classpath:/api/");
  }

  @Autowired private SpanCustomizer spanCustomizer;
  private String mdcRequestIdKey = "X-Request-ID";
  private String mdcCorrelationIdKey = "X-Correlation-ID";

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest httpRequest, HttpServletResponse httpResponse, Object handler)
              throws Exception {

            // get an mdc id from the request (if not found, create one), and pass it along in the response
            String requestId = getMDCRequestId(httpRequest);
            httpResponse.addHeader(mdcRequestIdKey, requestId);

            // add tags to Stackdriver traces
            spanCustomizer.tag("requestId", requestId);

            return true;
          }
        });
  }

  private Hashids hashids = new Hashids("requestIdSalt", 8);

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
