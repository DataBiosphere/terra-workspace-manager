import org.springframework.cloud.gcp.logging.StackdriverJsonLayout

appender("Console-Json", ConsoleAppender) {
    encoder(LayoutWrappingEncoder) {
        layout(StackdriverJsonLayout) {
            // Fields can be modified here.
        }
    }
}
root(INFO, ["Console-Json"])