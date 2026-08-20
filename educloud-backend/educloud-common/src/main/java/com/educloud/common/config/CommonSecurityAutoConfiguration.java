package com.educloud.common.config;

import com.educloud.common.security.SecurityContextFacade;
import com.educloud.common.security.SpringSecurityContextFacade;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

@AutoConfiguration(after = CommonServletWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityContextFacade.class)
    SpringSecurityContextFacade commonSecurityContextFacade() {
        return new SpringSecurityContextFacade(SecurityContextHolder.getContextHolderStrategy());
    }
}
