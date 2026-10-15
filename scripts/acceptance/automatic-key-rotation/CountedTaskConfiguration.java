package acceptance;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only time fixture. Never included in the production application JAR.
 * The class name is required by the existing T26 isolated runtime launcher.
 */
@Configuration(proxyBeanMethods = false)
public class CountedTaskConfiguration {
    @Bean
    RotationFixtureController rotationFixtureController(JdbcTemplate jdbc, Scheduler scheduler) {
        return new RotationFixtureController(jdbc, scheduler);
    }
}

@RestController
class RotationFixtureController {
    private final JdbcTemplate jdbc;
    private final Scheduler scheduler;

    RotationFixtureController(JdbcTemplate jdbc, Scheduler scheduler) {
        this.jdbc = jdbc;
        this.scheduler = scheduler;
    }

    @PostMapping("/api/acceptance/automatic-rotation:due")
    Map<String, Boolean> due() throws Exception {
        // Advance only the scheduled time; do not manufacture key/state outcomes.
        jdbc.update("UPDATE summer_security_key_rotation SET next_rotation_at=?, version=version+1 WHERE rotation_id=1",
                Timestamp.from(Instant.now().minusSeconds(60)));
        return wake();
    }

    @PostMapping("/api/acceptance/automatic-rotation:wake")
    Map<String, Boolean> wake() throws Exception {
        scheduler.triggerJob(new JobKey("automatic-key-rotation", "summer-security"));
        return Map.of("scheduled", true);
    }
}
