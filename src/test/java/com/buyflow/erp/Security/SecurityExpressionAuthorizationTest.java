package com.buyflow.erp.Security;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExpressionAuthorizationTest {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void adminRoleCanAccessBusinessApisWithoutDetailedPermissions() {
        List<String> expressions = List.of(
                SecurityExpressions.RECEIPTS_READ,
                SecurityExpressions.RECEIPTS_WRITE,
                SecurityExpressions.PRODUCTS_READ,
                SecurityExpressions.PRODUCTS_WRITE,
                SecurityExpressions.SUPPLIERS_READ,
                SecurityExpressions.SUPPLIERS_WRITE,
                SecurityExpressions.WAREHOUSES_READ,
                SecurityExpressions.WAREHOUSES_WRITE,
                SecurityExpressions.STOCK_HISTORY_READ,
                SecurityExpressions.PURCHASE_REQUESTS_READ,
                SecurityExpressions.PURCHASE_ORDERS_READ
        );

        expressions.forEach(expression ->
                assertThat(evaluate(expression, PermissionCodes.ROLE_ADMIN))
                        .as(expression)
                        .isTrue()
        );
    }

    @Test
    void roleReadAndWritePermissionsAreSeparated() {
        assertThat(evaluate(SecurityExpressions.ROLES_READ, PermissionCodes.ROLE_ADMIN)).isTrue();
        assertThat(evaluate(SecurityExpressions.ROLES_READ, PermissionCodes.ROLES_READ)).isTrue();
        assertThat(evaluate(SecurityExpressions.ROLES_READ, PermissionCodes.ROLES_WRITE)).isTrue();
        assertThat(evaluate(SecurityExpressions.ROLES_READ, PermissionCodes.LEGACY_ROLE_MANAGE)).isTrue();

        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, PermissionCodes.ROLE_ADMIN)).isTrue();
        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, PermissionCodes.ROLES_WRITE)).isTrue();
        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, PermissionCodes.LEGACY_ROLE_MANAGE)).isTrue();

        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, PermissionCodes.ROLES_READ)).isFalse();
        assertThat(evaluate(SecurityExpressions.ROLES_READ, PermissionCodes.LEGACY_USER_MANAGE)).isFalse();
        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, PermissionCodes.LEGACY_USER_MANAGE)).isFalse();
        assertThat(evaluate(SecurityExpressions.ROLES_READ, "ROLE_TEAM_MANAGER")).isFalse();
    }

    @Test
    void userManageCanAccessUserExpressionsButNotRbacManagement() {
        assertThat(evaluate(SecurityExpressions.USERS_READ, PermissionCodes.LEGACY_USER_MANAGE)).isTrue();
        assertThat(evaluate(SecurityExpressions.USERS_WRITE, PermissionCodes.LEGACY_USER_MANAGE)).isTrue();
        assertThat(evaluate(SecurityExpressions.ROLES_READ, PermissionCodes.LEGACY_USER_MANAGE)).isFalse();
        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, PermissionCodes.LEGACY_USER_MANAGE)).isFalse();
    }

    @Test
    void userPermissionsGrantUserManagementButTeamManagerRoleAloneDoesNot() {
        assertThat(evaluate(SecurityExpressions.USERS_READ, PermissionCodes.USERS_READ)).isTrue();
        assertThat(evaluate(SecurityExpressions.USERS_READ, PermissionCodes.USERS_WRITE)).isTrue();
        assertThat(evaluate(SecurityExpressions.USERS_WRITE, PermissionCodes.USERS_WRITE)).isTrue();

        assertThat(evaluate(SecurityExpressions.USERS_READ, "ROLE_TEAM_MANAGER")).isFalse();
        assertThat(evaluate(SecurityExpressions.USERS_WRITE, "ROLE_TEAM_MANAGER")).isFalse();
        assertThat(evaluate("hasRole('ADMIN')", "ROLE_TEAM_MANAGER")).isFalse();
        assertThat(evaluate(SecurityExpressions.ROLES_WRITE, "ROLE_TEAM_MANAGER")).isFalse();
    }

    @Test
    void roleManagementPermissionsDoNotGrantUserManagementAccess() {
        assertThat(evaluate(SecurityExpressions.USERS_READ, PermissionCodes.ROLES_READ)).isFalse();
        assertThat(evaluate(SecurityExpressions.USERS_READ, PermissionCodes.ROLES_WRITE)).isFalse();
        assertThat(evaluate(SecurityExpressions.USERS_READ, PermissionCodes.LEGACY_ROLE_MANAGE)).isFalse();
    }

    @Test
    void adminOnlyExpressionRejectsDelegatedUserManagementPermissions() {
        assertThat(evaluate(SecurityExpressions.ADMIN, PermissionCodes.ROLE_ADMIN)).isTrue();
        assertThat(evaluate(SecurityExpressions.ADMIN, PermissionCodes.USERS_WRITE)).isFalse();
        assertThat(evaluate(SecurityExpressions.ADMIN, PermissionCodes.LEGACY_USER_MANAGE)).isFalse();
        assertThat(evaluate(SecurityExpressions.ADMIN, "ROLE_TEAM_MANAGER")).isFalse();
    }

    @Test
    void masterDataApisUseCanonicalPermissionsOnly() {
        assertThat(evaluate(SecurityExpressions.PRODUCTS_READ, PermissionCodes.PRODUCTS_READ)).isTrue();
        assertThat(evaluate(SecurityExpressions.PRODUCTS_READ, PermissionCodes.PRODUCTS_WRITE)).isTrue();
        assertThat(evaluate(SecurityExpressions.PRODUCTS_WRITE, PermissionCodes.PRODUCTS_READ)).isFalse();
        assertThat(evaluate(SecurityExpressions.PRODUCTS_WRITE, PermissionCodes.PRODUCTS_WRITE)).isTrue();

        assertThat(evaluate(SecurityExpressions.SUPPLIERS_READ, PermissionCodes.SUPPLIERS_READ)).isTrue();
        assertThat(evaluate(SecurityExpressions.SUPPLIERS_READ, PermissionCodes.SUPPLIERS_WRITE)).isTrue();
        assertThat(evaluate(SecurityExpressions.SUPPLIERS_WRITE, PermissionCodes.SUPPLIERS_READ)).isFalse();
        assertThat(evaluate(SecurityExpressions.SUPPLIERS_WRITE, PermissionCodes.SUPPLIERS_WRITE)).isTrue();

        assertThat(evaluate(SecurityExpressions.PRODUCTS_READ, "PRODUCT_READ")).isFalse();
        assertThat(evaluate(SecurityExpressions.PRODUCTS_WRITE, "PRODUCT_WRITE")).isFalse();
        assertThat(evaluate(SecurityExpressions.SUPPLIERS_READ, "SUPPLIER_READ")).isFalse();
        assertThat(evaluate(SecurityExpressions.SUPPLIERS_WRITE, "SUPPLIER_MANAGE")).isFalse();
    }

    private boolean evaluate(String expression, String... authorities) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "test-user",
                "n/a",
                Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
        authentication.setAuthenticated(true);

        TestSecurityExpressionRoot root = new TestSecurityExpressionRoot(authentication);
        StandardEvaluationContext context = new StandardEvaluationContext(root);
        return Boolean.TRUE.equals(parser.parseExpression(expression).getValue(context, Boolean.class));
    }

    private static final class TestSecurityExpressionRoot extends SecurityExpressionRoot {

        private TestSecurityExpressionRoot(TestingAuthenticationToken authentication) {
            super(authentication);
        }
    }
}
