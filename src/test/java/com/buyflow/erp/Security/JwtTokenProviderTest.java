package com.buyflow.erp.Security;

import com.buyflow.erp.Entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            "buyflow-test-secret-key-32-bytes-minimum",
            60
    );

    @Test
    void existingJwtKeepsOriginalPermissionClaimsUntilANewTokenIsIssued() {
        User user = new User();
        user.setUserId(1L);
        user.setLoginId("worker");
        user.setUserName("Worker");
        user.setEmail("worker@buyflow.local");
        user.setPassword("encoded");
        user.setStatus("ACTIVE");
        user.setUseYn("Y");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        List<String> permissionsAtLogin = new ArrayList<>(List.of("dashboard.read"));
        String existingToken = jwtTokenProvider.generateAccessToken(
                user,
                List.of("VIEWER"),
                permissionsAtLogin
        );

        permissionsAtLogin.clear();
        permissionsAtLogin.add("roles.write");

        assertThat(jwtTokenProvider.getPermissions(existingToken))
                .containsExactly("dashboard.read");
        assertThat(jwtTokenProvider.getPermissions(existingToken))
                .doesNotContain("roles.write");

        String newToken = jwtTokenProvider.generateAccessToken(
                user,
                List.of("VIEWER"),
                permissionsAtLogin
        );

        assertThat(jwtTokenProvider.getPermissions(newToken))
                .containsExactly("roles.write");
    }
}
