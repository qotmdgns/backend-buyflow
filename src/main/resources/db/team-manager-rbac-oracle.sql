-- Scoped role delegation seed.
-- Safe to run repeatedly. Grants no direct system-management permission.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_DEPT_ROLE_ASSIGN_RULES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_DEPT_ROLE_ASSIGN_RULES START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'DEPARTMENT_ROLE_ASSIGN_RULES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE DEPARTMENT_ROLE_ASSIGN_RULES (
                RULE_ID NUMBER PRIMARY KEY,
                DEPARTMENT_NAME VARCHAR2(100) NOT NULL,
                ROLE_CODE VARCHAR2(30) NOT NULL,
                BUSINESS_AREA VARCHAR2(50),
                DESCRIPTION VARCHAR2(200),
                SORT_ORDER NUMBER DEFAULT 0,
                USE_YN CHAR(1) DEFAULT ''Y'' NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT UK_DEPT_ROLE_ASSIGN_RULE UNIQUE (DEPARTMENT_NAME, ROLE_CODE)
            )';
    END IF;
END;
/

DECLARE
    v_data_type VARCHAR2(30);
BEGIN
    SELECT DATA_TYPE
      INTO v_data_type
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'DEPARTMENT_ROLE_ASSIGN_RULES'
       AND COLUMN_NAME = 'USE_YN';

    IF v_data_type = 'VARCHAR2' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE DEPARTMENT_ROLE_ASSIGN_RULES MODIFY (USE_YN CHAR(1) DEFAULT ''Y'' NOT NULL)';
    END IF;
END;
/

MERGE INTO ROLES r
USING (
    SELECT 'TEAM_MANAGER' ROLE_CODE,
           '부서 팀장' ROLE_NAME,
           'BUSINESS' ROLE_GROUP,
           '자기 부서 사용자에게 허용된 업무 역할만 부여' DESCRIPTION,
           4 SORT_ORDER
      FROM DUAL
) src
ON (r.ROLE_CODE = src.ROLE_CODE)
WHEN MATCHED THEN
    UPDATE SET
        r.ROLE_NAME = src.ROLE_NAME,
        r.ROLE_GROUP = src.ROLE_GROUP,
        r.DESCRIPTION = src.DESCRIPTION,
        r.SORT_ORDER = src.SORT_ORDER,
        r.USE_YN = 'Y',
        r.UPDATED_AT = SYSTIMESTAMP
WHEN NOT MATCHED THEN
    INSERT (
        ROLE_ID,
        ROLE_CODE,
        ROLE_NAME,
        ROLE_GROUP,
        DESCRIPTION,
        SORT_ORDER,
        USE_YN,
        CREATED_AT,
        UPDATED_AT
    )
    VALUES (
        SEQ_ROLES.NEXTVAL,
        src.ROLE_CODE,
        src.ROLE_NAME,
        src.ROLE_GROUP,
        src.DESCRIPTION,
        src.SORT_ORDER,
        'Y',
        SYSTIMESTAMP,
        SYSTIMESTAMP
    );

COMMIT;

MERGE INTO ROLE_PERMISSIONS rp
USING (
    SELECT r.ROLE_ID, p.PERMISSION_ID
    FROM ROLES r
    JOIN PERMISSIONS p ON p.PERMISSION_CODE IN (
        'dashboard.read',
        'users.read',
        'users.write',
        'roles.read',
        'roles.write'
    )
    WHERE r.ROLE_CODE = 'TEAM_MANAGER'
      AND p.USE_YN = 'Y'
) src
ON (rp.ROLE_ID = src.ROLE_ID AND rp.PERMISSION_ID = src.PERMISSION_ID)
WHEN NOT MATCHED THEN
    INSERT (ROLE_PERMISSION_ID, ROLE_ID, PERMISSION_ID, CREATED_AT)
    VALUES (SEQ_ROLE_PERMISSIONS.NEXTVAL, src.ROLE_ID, src.PERMISSION_ID, SYSTIMESTAMP);

COMMIT;

MERGE INTO DEPARTMENT_ROLE_ASSIGN_RULES rule
USING (
    SELECT '구매팀' DEPARTMENT_NAME, 'REQUESTER' ROLE_CODE, '구매 요청' BUSINESS_AREA, '구매 요청 생성과 조회 업무' DESCRIPTION, 10 SORT_ORDER FROM DUAL
    UNION ALL SELECT '구매팀', 'APPROVER', '승인 처리', '구매 요청 승인과 반려 업무', 20 FROM DUAL
    UNION ALL SELECT '구매팀', 'MANAGER', '구매 관리', '구매 요청, 승인, 발주 흐름 관리', 30 FROM DUAL
    UNION ALL SELECT '구매팀', 'VIEWER', '조회', '주요 현황 조회', 90 FROM DUAL
    UNION ALL SELECT '물류운영팀', 'WAREHOUSE', '창고/입고/검수', '창고, 입고, 검수, 재고 처리 업무', 10 FROM DUAL
    UNION ALL SELECT '물류운영팀', 'VIEWER', '조회', '주요 현황 조회', 90 FROM DUAL
    UNION ALL SELECT '재고관리팀', 'WAREHOUSE', '재고 관리', '재고 현황, 재고 이력, 재고 조정 업무', 10 FROM DUAL
    UNION ALL SELECT '재고관리팀', 'VIEWER', '조회', '주요 현황 조회', 90 FROM DUAL
    UNION ALL SELECT '영업팀', 'REQUESTER', '구매 요청', '영업 활동에 필요한 구매 요청 업무', 10 FROM DUAL
    UNION ALL SELECT '영업팀', 'VIEWER', '조회', '주요 현황 조회', 90 FROM DUAL
    UNION ALL SELECT '시스템관리팀', 'VIEWER', '조회', '관리자 권한 없이 주요 현황 조회만 허용', 90 FROM DUAL
) src
ON (rule.DEPARTMENT_NAME = src.DEPARTMENT_NAME AND rule.ROLE_CODE = src.ROLE_CODE)
WHEN MATCHED THEN
    UPDATE SET
        rule.BUSINESS_AREA = src.BUSINESS_AREA,
        rule.DESCRIPTION = src.DESCRIPTION,
        rule.SORT_ORDER = src.SORT_ORDER,
        rule.USE_YN = 'Y',
        rule.UPDATED_AT = SYSTIMESTAMP
WHEN NOT MATCHED THEN
    INSERT (
        RULE_ID,
        DEPARTMENT_NAME,
        ROLE_CODE,
        BUSINESS_AREA,
        DESCRIPTION,
        SORT_ORDER,
        USE_YN,
        CREATED_AT,
        UPDATED_AT
    )
    VALUES (
        SEQ_DEPT_ROLE_ASSIGN_RULES.NEXTVAL,
        src.DEPARTMENT_NAME,
        src.ROLE_CODE,
        src.BUSINESS_AREA,
        src.DESCRIPTION,
        src.SORT_ORDER,
        'Y',
        SYSTIMESTAMP,
        SYSTIMESTAMP
    );

COMMIT;
