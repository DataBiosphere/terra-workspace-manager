package bio.terra.workspace.common.utils;

import brave.Span;
import brave.Tracer;

public class TraceUtils {

  public static Tracer.SpanInScope nextSpan(Tracer tracer, String traceName) {
    Span newSpan = tracer.nextSpan().name(traceName);
    return tracer.withSpanInScope(newSpan.start());
  }
}
