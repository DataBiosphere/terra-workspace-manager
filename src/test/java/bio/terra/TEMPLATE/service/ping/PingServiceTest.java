package bio.terra.TEMPLATE.service.ping;

// TODO: TEMPLATE: This test is an example of that calls code directly; not through Spring or a controller.
//  It is typical of a low-level unit test. Since we are not using any Spring, no autowiring is done.
//  You can use @Tag to group tests and add tasks in Gradle to run groups.

import bio.terra.TEMPLATE.service.ping.exception.BadPingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

@Tag("unit")
public class PingServiceTest {
    private PingService pingService;

    @BeforeEach
    public void before() {
        pingService = new PingService();
    }

    @Test
    public void testPong() {
        String result = pingService.computePing("test");
        assertThat("result starts with pong", result, startsWith("pong:"));
    }

    @Test
    public void testBadPing() {
        Assertions.assertThrows(BadPingException.class, () -> {
            pingService.computePing(null);
        });
    }
}
