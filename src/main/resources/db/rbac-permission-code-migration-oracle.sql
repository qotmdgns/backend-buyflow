-- Migrates retired permission codes to canonical codes without dropping access.
-- Run after rbac-seed-oracle.sql and department-permissions-oracle.sql.
-- Safe to run repeatedly.

ALTER SESSION DISABLE PARALLEL DML;

INSERT INTO ROLE_PERMISSIONS (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
SELECT SEQ_ROLE_PERMISSIONS.NEXTVAL,
       legacy_mapping.ROLE_ID,
       canonical_permission.PERMISSION_ID,
       SYSTIMESTAMP
FROM (
    SELECT rp.ROLE_ID,
           code_mapping.CANONICAL_CODE
    FROM ROLE_PERMISSIONS rp
    JOIN PERMISSIONS legacy_permission
      ON legacy_permission.PERMISSION_ID = rp.PERMISSION_ID
    JOIN (
        SELECT 'PRODUCT_READ' LEGACY_CODE, 'products.read' CANONICAL_CODE FROM DUAL
        UNION ALL SELECT 'PRODUCT_WRITE', 'products.write' FROM DUAL
        UNION ALL SELECT 'SUPPLIER_READ', 'suppliers.read' FROM DUAL
        UNION ALL SELECT 'SUPPLIER_MANAGE', 'suppliers.write' FROM DUAL
        UNION ALL SELECT 'PURCHASE_REQUEST_CREATE', 'purchase-requests.write' FROM DUAL
        UNION ALL SELECT 'PURCHASE_REQUEST_APPROVE', 'purchase-requests.read' FROM DUAL
        UNION ALL SELECT 'PURCHASE_REQUEST_APPROVE', 'approvals.process' FROM DUAL
    ) code_mapping
      ON code_mapping.LEGACY_CODE = legacy_permission.PERMISSION_CODE
) legacy_mapping
JOIN PERMISSIONS canonical_permission
  ON canonical_permission.PERMISSION_CODE = legacy_mapping.CANONICAL_CODE
 AND canonical_permission.USE_YN = 'Y'
WHERE NOT EXISTS (
    SELECT 1
    FROM ROLE_PERMISSIONS existing_mapping
    WHERE existing_mapping.ROLE_ID = legacy_mapping.ROLE_ID
      AND existing_mapping.PERMISSION_ID = canonical_permission.PERMISSION_ID
);

COMMIT;

INSERT INTO DEPARTMENT_PERMISSIONS (
    DEPARTMENT_PERMISSION_ID,
    DEPARTMENT_NAME,
    PERMISSION_ID,
    CREATED_AT
)
SELECT SEQ_DEPARTMENT_PERMISSIONS.NEXTVAL,
       legacy_mapping.DEPARTMENT_NAME,
       canonical_permission.PERMISSION_ID,
       SYSTIMESTAMP
FROM (
    SELECT dp.DEPARTMENT_NAME,
           code_mapping.CANONICAL_CODE
    FROM DEPARTMENT_PERMISSIONS dp
    JOIN PERMISSIONS legacy_permission
      ON legacy_permission.PERMISSION_ID = dp.PERMISSION_ID
    JOIN (
        SELECT 'PRODUCT_READ' LEGACY_CODE, 'products.read' CANONICAL_CODE FROM DUAL
        UNION ALL SELECT 'PRODUCT_WRITE', 'products.write' FROM DUAL
        UNION ALL SELECT 'SUPPLIER_READ', 'suppliers.read' FROM DUAL
        UNION ALL SELECT 'SUPPLIER_MANAGE', 'suppliers.write' FROM DUAL
        UNION ALL SELECT 'PURCHASE_REQUEST_CREATE', 'purchase-requests.write' FROM DUAL
        UNION ALL SELECT 'PURCHASE_REQUEST_APPROVE', 'purchase-requests.read' FROM DUAL
        UNION ALL SELECT 'PURCHASE_REQUEST_APPROVE', 'approvals.process' FROM DUAL
    ) code_mapping
      ON code_mapping.LEGACY_CODE = legacy_permission.PERMISSION_CODE
) legacy_mapping
JOIN PERMISSIONS canonical_permission
  ON canonical_permission.PERMISSION_CODE = legacy_mapping.CANONICAL_CODE
 AND canonical_permission.USE_YN = 'Y'
WHERE NOT EXISTS (
    SELECT 1
    FROM DEPARTMENT_PERMISSIONS existing_mapping
    WHERE existing_mapping.DEPARTMENT_NAME = legacy_mapping.DEPARTMENT_NAME
      AND existing_mapping.PERMISSION_ID = canonical_permission.PERMISSION_ID
);

COMMIT;

UPDATE PERMISSIONS
SET USE_YN = 'N',
    UPDATED_AT = SYSTIMESTAMP
WHERE PERMISSION_CODE IN (
    'PRODUCT_READ',
    'PRODUCT_WRITE',
    'SUPPLIER_READ',
    'SUPPLIER_MANAGE',
    'PURCHASE_REQUEST_CREATE',
    'PURCHASE_REQUEST_APPROVE'
)
  AND USE_YN <> 'N';

COMMIT;

SELECT PERMISSION_CODE, USE_YN
FROM PERMISSIONS
WHERE PERMISSION_CODE IN (
    'PRODUCT_READ',
    'PRODUCT_WRITE',
    'SUPPLIER_READ',
    'SUPPLIER_MANAGE',
    'PURCHASE_REQUEST_CREATE',
    'PURCHASE_REQUEST_APPROVE',
    'products.read',
    'products.write',
    'suppliers.read',
    'suppliers.write',
    'purchase-requests.read',
    'purchase-requests.write',
    'approvals.process'
)
ORDER BY PERMISSION_CODE;
