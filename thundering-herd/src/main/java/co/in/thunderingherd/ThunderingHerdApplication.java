package co.in.thunderingherd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
public class ThunderingHerdApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThunderingHerdApplication.class, args);

        System.out.println("""
                
                ╔════════════════════════════════════════════════════════════════╗
                ║        Thundering Herd Resolver - Started Successfully        ║
                ╠════════════════════════════════════════════════════════════════╣
                ║  🌐 Web UI:        http://localhost:8080                      ║
                ║  📊 Metrics:       http://localhost:8080/actuator/metrics     ║
                ║  🔍 Prometheus:    http://localhost:8080/actuator/prometheus  ║
                ║  💚 Health:        http://localhost:8080/actuator/health      ║
                ║                                                                ║
                ║  Demo Endpoints:                                              ║
                ║    /demo/baseline        - No mitigation                      ║
                ║    /demo/singleflight    - Single-flight only                 ║
                ║    /demo/full            - Full solution                      ║
                ║    /demo/stampede        - Cache stampede test                ║
                ╚════════════════════════════════════════════════════════════════╝
                """
        );
    }
}
