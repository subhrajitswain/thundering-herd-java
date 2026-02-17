package co.in.thunderingherd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "thundering-herd")
public class ThunderingHerdProperties {

    private CacheConfig cache = new CacheConfig();
    private SingleFlightConfig singleflight = new SingleFlightConfig();

    @Data
    public static class CacheConfig {
        private long defaultTtl = 60;
        private int jitterPercentage = 20;
        private double beta = 2.0;
        private long negativeCacheTtl = 60;
    }

    @Data
    public static class SingleFlightConfig {
        private long timeout = 10000;
    }
}
