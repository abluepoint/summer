package acceptance;

import com.abluepoint.summer.sample.task.RegisteredTaskHandler;
import java.util.Map;
import java.util.function.IntConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Test-only registered business handler, executed by the real Quartz path.
 * Produces progress through the public handler API; never inserts event rows directly.
 * No additional controller or authentication substitute is installed.
 */
@Configuration(proxyBeanMethods = false)
public class SseTaskConfiguration {
    @Bean RegisteredTaskHandler acceptanceProgressBurst() {
        return new RegisteredTaskHandler() {
            public String taskType() { return "acceptance.sse-burst"; }
            public void execute(Map<String,Object> parameters, IntConsumer progress) {
                int count = ((Number) parameters.getOrDefault("count", 700)).intValue();
                if (count < 1 || count > 30000) throw new IllegalArgumentException("Invalid acceptance event count");
                for (int i = 0; i < count; i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    progress.accept(i % 101);
                }
            }
        };
    }
}
