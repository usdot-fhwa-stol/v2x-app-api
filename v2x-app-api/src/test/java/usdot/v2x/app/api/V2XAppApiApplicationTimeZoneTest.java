package usdot.v2x.app.api;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V2XAppApiApplicationTimeZoneTest {

    @Test
    void useUtcDefaultTimeZone_setsJvmDefaultToUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

            V2XAppApiApplication.useUtcDefaultTimeZone();

            assertEquals("UTC", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
