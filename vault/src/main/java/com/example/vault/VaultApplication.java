package com.example.vault;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import javax.sql.DataSource;
import java.time.Instant;

import static org.springframework.web.servlet.function.RouterFunctions.route;

@SpringBootApplication
public class VaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultApplication.class, args);
    }


    @Bean
    @RefreshScope
    DataSource dataSource(DataSourceProperties properties) {
        var log = LogFactory.getLog(getClass());
        var db = DataSourceBuilder //
                .create()//
                .url(properties.getUrl()) //
                .username(properties.getUsername()) //
                .password(properties.getPassword()) //
                .build();
        log.info(properties.getUrl() + ':' +
                properties.getUsername() + ':' +
                properties.getPassword());
        return db;
    }


    record Payment(String id, String name, String billingAddress, Instant createdAt) {
    }

    @Bean
    RouterFunction<ServerResponse> httpEndpoints(JdbcClient jdbcClient) {
        return route()
                .GET("/payments", request -> {
                    var payments = jdbcClient
                            .sql("select * from payments")
                            .query(new DataClassRowMapper<>(Payment.class))
                            .list();
                    return ServerResponse.ok().body(payments);
                })
                .build();
    }


}



@Configuration
@EnableScheduling
class VaultConfiguration {

    private final Log log = LogFactory.getLog(getClass());

    private final ContextRefresher contextRefresher;

    private final ApplicationEventPublisher publisher;

    VaultConfiguration(@Value("${spring.cloud.vault.database.role}") String databaseRole,
                       @Value("${spring.cloud.vault.database.backend}") String databaseBackend,
                       SecretLeaseContainer leaseContainer,
                       ContextRefresher contextRefresher, ApplicationEventPublisher publisher) {
        this.contextRefresher = contextRefresher;
        this.publisher = publisher;
        leaseContainer.addLeaseListener(event -> {
            if (String.format("%s/creds/%s", databaseBackend, databaseRole)
                    .equals(event.getSource().getPath())) {
                if (event instanceof SecretLeaseExpiredEvent) {
                    publisher.publishEvent(new RefreshEvent(this, null, "vault or bust!"));
                }
            }
        });
    }

    @Scheduled(initialDelayString="${kv.refresh-interval}",
            fixedDelayString = "${kv.refresh-interval}")
    void refresher() {
        contextRefresher.refresh();
        log.info("refresh KV secret");
    }
}







