package com.buyflow.erp.Controller;

import com.buyflow.erp.Dto.DashboardDto;
import com.buyflow.erp.Security.JwtTokenProvider;
import com.buyflow.erp.Security.PermissionCodes;
import com.buyflow.erp.Service.DashboardService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(DashboardControllerSecurityTest.MethodSecurityTestConfig.class)
class DashboardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(authorities = PermissionCodes.DASHBOARD_READ)
    void dashboardReadCanAccessApiRoute() throws Exception {
        DashboardDto.Response response = new DashboardDto.Response(
                "now",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                List.of(),
                0,
                List.of(),
                new DashboardDto.SummaryDetails(List.of(), List.of(), List.of(), List.of(), List.of())
        );
        when(dashboardService.getDashboard(6)).thenReturn(response);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionCodes.PRODUCTS_READ)
    void unrelatedReadPermissionCannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
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
