package com.buyflow.erp.Security;

public final class PermissionCodes {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static final String PURCHASE_REQUESTS_READ = "purchase-requests.read";
    public static final String PURCHASE_REQUESTS_WRITE = "purchase-requests.write";
    public static final String PURCHASE_ORDERS_READ = "purchase-orders.read";
    public static final String PURCHASE_ORDERS_WRITE = "purchase-orders.write";
    public static final String RECEIPTS_READ = "receipts.read";
    public static final String RECEIPTS_WRITE = "receipts.write";
    public static final String APPROVALS_READ = "approvals.read";
    public static final String APPROVALS_PROCESS = "approvals.process";
    public static final String INSPECTIONS_READ = "inspections.read";
    public static final String INSPECTIONS_PROCESS = "inspections.process";
    public static final String STOCK_READ = "stock.read";
    public static final String STOCK_ADJUST = "stock.adjust";
    public static final String USERS_READ = "users.read";
    public static final String USERS_WRITE = "users.write";
    public static final String DASHBOARD_READ = "dashboard.read";
    public static final String LEGACY_USER_MANAGE = "USER_MANAGE";

    private PermissionCodes() {
    }
}
