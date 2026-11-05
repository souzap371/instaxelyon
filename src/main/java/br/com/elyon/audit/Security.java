package br.com.elyon.audit;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class Security {
    @Bean
    SecurityFilterChain filter(HttpSecurity h) throws Exception {
        return h.authorizeHttpRequests(a -> a.requestMatchers("/style.css", "/images/**", "/login").permitAll()
                .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true))
                .logout(l -> l.logoutSuccessUrl("/login?logout")).build();
    }

    @Bean
    UserDetailsService users(@Value("${APP_PASSWORD}") String password) {
        if (password.length() < 12)
            throw new IllegalArgumentException("APP_PASSWORD deve conter ao menos 12 caracteres");
        return new InMemoryUserDetailsManager(User.withUsername("elyon")
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("ADMIN").build());
    }
}
