ALTER SESSION DISABLE PARALLEL DML;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_USERS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_USERS START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_ROLES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_ROLES START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_PERMISSIONS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_PERMISSIONS START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_USER_ROLES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_USER_ROLES START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_ROLE_PERMISSIONS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_ROLE_PERMISSIONS START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;
END;
/

MERGE INTO ROLES r
USING (
    SELECT 'ADMIN' ROLE_CODE, 'Administrator' ROLE_NAME, 'SYSTEM' ROLE_GROUP, 'Full system management role' DESCRIPTION, 1 SORT_ORDER FROM DUAL
    UNION ALL SELECT 'REQUESTER', 'Requester', 'BUSINESS', 'Can create and view purchase requests', 2 FROM DUAL
    UNION ALL SELECT 'APPROVER', 'Approver', 'BUSINESS', 'Can approve or reject purchase requests', 3 FROM DUAL
    UNION ALL SELECT 'MANAGER', 'Manager', 'BUSINESS', 'Can manage purchase request, approval, and order flows', 5 FROM DUAL
    UNION ALL SELECT 'WAREHOUSE', 'Warehouse operator', 'BUSINESS', 'Can manage warehouse, receipt, inspection, and stock flows', 6 FROM DUAL
    UNION ALL SELECT 'VIEWER', 'Viewer', 'BUSINESS', 'Default read-only role for new users', 90 FROM DUAL
) src
ON (r.ROLE_CODE = src.ROLE_CODE)
WHEN NOT MATCHED THEN
    INSERT (ROLE_ID, ROLE_CODE, ROLE_NAME, ROLE_GROUP, DESCRIPTION, SORT_ORDER, USE_YN, CREATED_AT, UPDATED_AT)
    VALUES (SEQ_ROLES.NEXTVAL, src.ROLE_CODE, src.ROLE_NAME, src.ROLE_GROUP, src.DESCRIPTION, src.SORT_ORDER, 'Y', SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

MERGE INTO PERMISSIONS p
USING (
    SELECT 'USER_MANAGE' PERMISSION_CODE, 'Manage users' PERMISSION_NAME, 'SYSTEM' PERMISSION_GROUP, 'View users, approve users, and assign roles' DESCRIPTION FROM DUAL
    UNION ALL SELECT 'ROLE_MANAGE', 'Manage roles', 'SYSTEM', 'Manage roles and permissions' FROM DUAL
    UNION ALL SELECT 'dashboard.read', 'Read dashboard', 'DASHBOARD', 'View dashboard summary' FROM DUAL
    UNION ALL SELECT 'products.read', 'Read products', 'MASTER_DATA', 'View product list and detail' FROM DUAL
    UNION ALL SELECT 'products.write', 'Write products', 'MASTER_DATA', 'Create and update products' FROM DUAL
    UNION ALL SELECT 'suppliers.read', 'Read suppliers', 'MASTER_DATA', 'View supplier list and detail' FROM DUAL
    UNION ALL SELECT 'suppliers.write', 'Write suppliers', 'MASTER_DATA', 'Create, update, and deactivate suppliers' FROM DUAL
    UNION ALL SELECT 'warehouses.read', 'Read warehouses', 'MASTER_DATA', 'View warehouse list and detail' FROM DUAL
    UNION ALL SELECT 'warehouses.write', 'Write warehouses', 'MASTER_DATA', 'Create and update warehouses' FROM DUAL
    UNION ALL SELECT 'purchase-requests.read', 'Read purchase requests', 'PURCHASE', 'View purchase request list and detail' FROM DUAL
    UNION ALL SELECT 'purchase-requests.write', 'Write purchase requests', 'PURCHASE', 'Create and update purchase requests' FROM DUAL
    UNION ALL SELECT 'approvals.read', 'Read approvals', 'PURCHASE', 'View approval list and history' FROM DUAL
    UNION ALL SELECT 'approvals.process', 'Process approvals', 'PURCHASE', 'Approve or reject approval requests' FROM DUAL
    UNION ALL SELECT 'purchase-orders.read', 'Read purchase orders', 'PURCHASE', 'View purchase order list and detail' FROM DUAL
    UNION ALL SELECT 'purchase-orders.write', 'Write purchase orders', 'PURCHASE', 'Create and update purchase orders' FROM DUAL
    UNION ALL SELECT 'receipts.read', 'Read receipts', 'PURCHASE', 'View receipt list and detail' FROM DUAL
    UNION ALL SELECT 'receipts.write', 'Write receipts', 'PURCHASE', 'Create and update receipts' FROM DUAL
    UNION ALL SELECT 'inspections.read', 'Read inspections', 'PURCHASE', 'View inspection list and detail' FROM DUAL
    UNION ALL SELECT 'inspections.process', 'Process inspections', 'PURCHASE', 'Register and complete inspections' FROM DUAL
    UNION ALL SELECT 'stock.read', 'Read stock', 'STOCK', 'View current stock' FROM DUAL
    UNION ALL SELECT 'stock.adjust', 'Adjust stock', 'STOCK', 'Adjust current stock' FROM DUAL
    UNION ALL SELECT 'stock-history.read', 'Read stock history', 'STOCK', 'View stock movement history' FROM DUAL
    UNION ALL SELECT 'users.read', 'Read users', 'SYSTEM', 'View user list and detail' FROM DUAL
    UNION ALL SELECT 'users.write', 'Write users', 'SYSTEM', 'Update user profile and status' FROM DUAL
    UNION ALL SELECT 'roles.read', 'Read roles', 'SYSTEM', 'View roles and permissions' FROM DUAL
    UNION ALL SELECT 'roles.write', 'Write roles', 'SYSTEM', 'Manage roles and permissions' FROM DUAL
) src
ON (p.PERMISSION_CODE = src.PERMISSION_CODE)
WHEN NOT MATCHED THEN
    INSERT (PERMISSION_ID, PERMISSION_CODE, PERMISSION_NAME, PERMISSION_GROUP, DESCRIPTION, USE_YN, CREATED_AT, UPDATED_AT)
    VALUES (SEQ_PERMISSIONS.NEXTVAL, src.PERMISSION_CODE, src.PERMISSION_NAME, src.PERMISSION_GROUP, src.DESCRIPTION, 'Y', SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

MERGE INTO USERS u
USING (
    SELECT 'admin' LOGIN_ID,
           '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' PASSWORD,
           'Administrator' USER_NAME,
           'admin@buyflow.local' EMAIL
    FROM DUAL
) src
ON (u.LOGIN_ID = src.LOGIN_ID)
WHEN NOT MATCHED THEN
    INSERT (USER_ID, LOGIN_ID, PASSWORD, USER_NAME, EMAIL, STATUS, USE_YN, CREATED_AT, UPDATED_AT)
    VALUES (SEQ_USERS.NEXTVAL, src.LOGIN_ID, src.PASSWORD, src.USER_NAME, src.EMAIL, 'ACTIVE', 'Y', SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

MERGE INTO USER_ROLES ur
USING (
    SELECT u.USER_ID, r.ROLE_ID
    FROM USERS u
    JOIN ROLES r ON r.ROLE_CODE = 'ADMIN'
    WHERE u.LOGIN_ID = 'admin'
) src
ON (ur.USER_ID = src.USER_ID AND ur.ROLE_ID = src.ROLE_ID)
WHEN NOT MATCHED THEN
    INSERT (USER_ROLE_ID, USER_ID, ROLE_ID, CREATED_AT)
    VALUES (SEQ_USER_ROLES.NEXTVAL, src.USER_ID, src.ROLE_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    CROSS JOIN PERMISSIONS p
    WHERE r.ROLE_CODE = 'ADMIN'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    JOIN PERMISSIONS p ON p.PERMISSION_CODE = 'purchase-requests.write'
    WHERE r.ROLE_CODE = 'REQUESTER'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    JOIN PERMISSIONS p ON p.PERMISSION_CODE IN ('purchase-requests.read', 'approvals.process')
    WHERE r.ROLE_CODE = 'APPROVER'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    JOIN PERMISSIONS p ON p.PERMISSION_CODE IN ('dashboard.read')
    WHERE r.ROLE_CODE = 'VIEWER'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    JOIN PERMISSIONS p ON p.PERMISSION_CODE IN (
        'dashboard.read',
        'warehouses.write',
        'receipts.write',
        'inspections.process',
        'stock.read',
        'stock.adjust',
        'stock-history.read'
    )
    WHERE r.ROLE_CODE = 'WAREHOUSE'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    JOIN PERMISSIONS p ON p.PERMISSION_CODE IN (
        'dashboard.read',
        'purchase-requests.write',
        'approvals.process',
        'purchase-orders.write'
    )
    WHERE r.ROLE_CODE = 'MANAGER'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;
