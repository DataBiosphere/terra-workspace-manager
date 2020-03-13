package bio.terra.TEMPLATE.service.ping;

import bio.terra.TEMPLATE.service.ping.exception.BadPingException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// TODO: TEMPLATE sample service component that handles the ping request

@Component
public class PingService {
    public String computePing(String message) {
        if (StringUtils.isEmpty(message)) {
            throw new BadPingException("No message to ping");
        }
        return "pong: " + message + "\n";
    }
}
