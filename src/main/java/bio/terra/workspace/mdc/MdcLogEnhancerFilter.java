package bio.terra.workspace.mdc;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import org.hashids.Hashids;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MdcLogEnhancerFilter implements Filter {

  private Hashids hashids = new Hashids("requestIdSalt", 8);

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

    /* Services calling Workspace Manager can supply an X-Request-ID or X-Correlation-ID
       HTTP header to trace requests through the system. X-Request-ID will be preferred over
       X-Correlation-ID. If neither header is present, Workspace Manager will generate one.
    */

    String requestId =
        ((requestId = httpRequest.getHeader("X-Request-ID")) != null)
            ? requestId
            : ((requestId = httpRequest.getHeader("X-Correlation-ID")) != null)
                ? requestId
                : generateRequestId();

    MDC.put("requestId", requestId);
    filterChain.doFilter(servletRequest, servletResponse);
  }

  private String generateRequestId() {
    long generatedLong = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);

    return hashids.encode(generatedLong);
  }
}
