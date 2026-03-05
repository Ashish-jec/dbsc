package com.example.dbsc.config;

import com.example.dbsc.dbsc.store.RegistrationChallengeStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class DbscFilterConfig {

    @Bean
    public FilterRegistrationBean<DbscRegistrationHeaderFilter> dbscRegistrationHeaderFilter(
            RegistrationChallengeStore registrationChallengeStore,
            DbscProperties dbscProperties) {
        FilterRegistrationBean<DbscRegistrationHeaderFilter> reg = new FilterRegistrationBean<>(
                new DbscRegistrationHeaderFilter(registrationChallengeStore, dbscProperties));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
