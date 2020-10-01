import org.springframework.cloud.gcp.logging.StackdriverJsonLayout

appender("Console-Json", ConsoleAppender) {
    encoder(LayoutWrappingEncoder) {
        layout(StackdriverJsonLayout) {
            includeTraceId = false
            includeSpanId = false
            // Other fields can be modified here.
        }
    }
}
root(INFO, ["Console-Json"])