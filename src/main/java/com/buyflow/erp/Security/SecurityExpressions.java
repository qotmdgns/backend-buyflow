package com.buyflow.erp.Security;

public final class SecurityExpressions {

    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String ADMIN = "hasAuthority('" + PermissionCodes.ROLE_ADMIN + "')";

    public static final String ROLES_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.ROLES_READ + "') or hasAuthority('" + PermissionCodes.ROLES_WRITE + "') or hasAuthority('" + PermissionCodes.LEGACY_ROLE_MANAGE + "')";
    public static final String ROLES_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.ROLES_WRITE + "') or hasAuthority('" + PermissionCodes.LEGACY_ROLE_MANAGE + "')";
    public static final String RBAC_MANAGE = ROLES_WRITE;

    public static final String PRODUCTS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.PRODUCTS_READ + "') or hasAuthority('" + PermissionCodes.PRODUCTS_WRITE + "')";
    public static final String PRODUCTS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.PRODUCTS_WRITE + "')";

    public static final String SUPPLIERS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.SUPPLIERS_READ + "') or hasAuthority('" + PermissionCodes.SUPPLIERS_WRITE + "')";
    public static final String SUPPLIERS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.SUPPLIERS_WRITE + "')";

    public static final String RECEIPTS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.RECEIPTS_READ + "') or hasAuthority('" + PermissionCodes.RECEIPTS_WRITE + "')";
    public static final String RECEIPTS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.RECEIPTS_WRITE + "')";

    public static final String WAREHOUSES_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.WAREHOUSES_READ + "') or hasAuthority('" + PermissionCodes.WAREHOUSES_WRITE + "')";
    public static final String WAREHOUSES_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.WAREHOUSES_WRITE + "')";

    public static final String STOCK_HISTORY_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.STOCK_HISTORY_READ + "')";

    public static final String PURCHASE_REQUESTS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.PURCHASE_REQUESTS_READ + "') or hasAuthority('" + PermissionCodes.PURCHASE_REQUESTS_WRITE + "')";
    public static final String PURCHASE_REQUESTS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.PURCHASE_REQUESTS_WRITE + "')";
    public static final String PURCHASE_ORDERS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.PURCHASE_ORDERS_READ + "') or hasAuthority('" + PermissionCodes.PURCHASE_ORDERS_WRITE + "')";
    public static final String PURCHASE_ORDERS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.PURCHASE_ORDERS_WRITE + "')";
    public static final String APPROVALS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.APPROVALS_READ + "') or hasAuthority('" + PermissionCodes.APPROVALS_PROCESS + "')";
    public static final String APPROVALS_PROCESS =
            ADMIN + " or hasAuthority('" + PermissionCodes.APPROVALS_PROCESS + "')";
    public static final String INSPECTIONS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.INSPECTIONS_READ + "') or hasAuthority('" + PermissionCodes.INSPECTIONS_PROCESS + "')";
    public static final String INSPECTIONS_PROCESS =
            ADMIN + " or hasAuthority('" + PermissionCodes.INSPECTIONS_PROCESS + "')";
    public static final String STOCK_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.STOCK_READ + "') or hasAuthority('" + PermissionCodes.STOCK_ADJUST + "')";
    public static final String STOCK_ADJUST =
            ADMIN + " or hasAuthority('" + PermissionCodes.STOCK_ADJUST + "')";
    public static final String USERS_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.USERS_READ + "') or hasAuthority('" + PermissionCodes.USERS_WRITE + "') or hasAuthority('" + PermissionCodes.LEGACY_USER_MANAGE + "')";
    public static final String USERS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.USERS_WRITE + "') or hasAuthority('" + PermissionCodes.LEGACY_USER_MANAGE + "')";
    public static final String DASHBOARD_READ =
            ADMIN + " or hasAuthority('" + PermissionCodes.DASHBOARD_READ + "')";

    private SecurityExpressions() {
    }
}
