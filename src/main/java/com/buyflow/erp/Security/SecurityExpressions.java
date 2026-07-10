package com.buyflow.erp.Security;

public final class SecurityExpressions {

    public static final String AUTHENTICATED = "isAuthenticated()";
    private static final String ADMIN = "hasAuthority('" + PermissionCodes.ROLE_ADMIN + "')";

    public static final String RECEIPTS_READ =
            "hasAuthority('receipts.read') or hasAuthority('receipts.write')";
    public static final String RECEIPTS_WRITE =
            "hasAuthority('receipts.write')";

    public static final String WAREHOUSES_READ =
            "hasAuthority('warehouses.read') or hasAuthority('warehouses.write')";
    public static final String WAREHOUSES_WRITE =
            "hasAuthority('warehouses.write')";

    public static final String STOCK_HISTORY_READ =
            "hasAuthority('stock-history.read')";

    public static final String PURCHASE_REQUESTS_READ =
            "hasAuthority('" + PermissionCodes.PURCHASE_REQUESTS_READ + "') or hasAuthority('" + PermissionCodes.PURCHASE_REQUESTS_WRITE + "')";
    public static final String PURCHASE_REQUESTS_WRITE =
            ADMIN + " or hasAuthority('" + PermissionCodes.PURCHASE_REQUESTS_WRITE + "')";
    public static final String PURCHASE_ORDERS_READ =
            "hasAuthority('" + PermissionCodes.PURCHASE_ORDERS_READ + "') or hasAuthority('" + PermissionCodes.PURCHASE_ORDERS_WRITE + "')";
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
