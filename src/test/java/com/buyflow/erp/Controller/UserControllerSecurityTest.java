package com.buyflow.erp.Controller;

import com.buyflow.erp.Security.PermissionCodes;
import com.buyflow.erp.Security.JwtTokenProvider;
import com.buyflow.erp.Service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(UserControllerSecurityTest.MethodSecurityTestConfig.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(authorities = PermissionCodes.USERS_WRITE)
    void usersWriteCannotReadGeneralUserList() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(authorities = PermissionCodes.LEGACY_USER_MANAGE)
    void legacyUserManageCannotDeactivateThroughGeneralUserApi() throws Exception {
        mockMvc.perform(delete("/users/2"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(authorities = PermissionCodes.ROLE_ADMIN)
    void adminCanReadGeneralUserList() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .httpBasic(withDefaults());
            return http.build();
        }
    }
}
