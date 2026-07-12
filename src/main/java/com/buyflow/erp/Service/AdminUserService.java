package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Dto.AdminUserProfileUpdateRequest;
import com.buyflow.erp.Dto.AdminUserDepartmentAuthorizationUpdateRequest;
import com.buyflow.erp.Dto.AdminUserResponse;
import com.buyflow.erp.Dto.AdminUserRoleUpdateRequest;
import com.buyflow.erp.Dto.AdminUserStatusUpdateRequest;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.RoleResponse;
import com.buyflow.erp.Dto.UserResponse;
import com.buyflow.erp.Entity.Role;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Entity.UserRole;
import com.buyflow.erp.Repository.DepartmentRoleAssignmentRuleRepository;
import com.buyflow.erp.Repository.RoleRepository;
import com.buyflow.erp.Repository.AuthUserRepository;
import com.buyflow.erp.Repository.UserRoleRepository;
import com.buyflow.erp.Security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String LEADER_KEYWORD = "팀장";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SECURITY_ADMIN = "SECURITY_ADMIN";
    private static final String ROLE_TEAM_MANAGER = "TEAM_MANAGER";
    private static final Set<String> PROTECTED_ROLE_CODES = Set.of(
            ROLE_ADMIN,
            ROLE_SECURITY_ADMIN,
            ROLE_TEAM_MANAGER
    );

    private final AuthUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final DepartmentRoleAssignmentRuleRepository departmentRoleAssignmentRuleRepository;
    private final DepartmentAuthorizationService departmentAuthorizationService;
    private final RbacQueryService rbacQueryService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> findAll(String currentLoginId) {
        User currentUser = findUserByLoginId(currentLoginId);

        if (isAdmin(currentUser)) {
            return userRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(this::toAdminUserResponse)
                    .toList();
        }

        requireUserReadPermission(currentUser);
        String departmentName = requireScopedDepartment(currentUser);
        return userRepository.findByDepartmentNameScoped(departmentName)
                .stream()
                .map(this::toAdminUserResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(
            String keyword,
            String departmentName,
            String status,
            String useYn,
            String jobRank,
            String roleCode,
            String departmentAuthorized,
            int page,
            int size,
            String currentLoginId
    ) {
        User currentUser = findUserByLoginId(currentLoginId);
        String effectiveDepartmentName = isAdmin(currentUser)
                ? normalizeText(departmentName)
                : requireScopedDepartment(requireUserReadPermission(currentUser));

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return PageResponse.from(userRepository.search(
                normalizeText(keyword),
                effectiveDepartmentName,
                normalizeText(status),
                normalizeText(useYn),
                normalizeJobRankFilter(jobRank),
                normalizeRoleCode(roleCode),
                normalizeDepartmentAuthorized(departmentAuthorized),
                pageRequest
        ).map(this::toAdminUserResponse));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse findById(Long userId, String currentLoginId) {
        User currentUser = findUserByLoginId(currentLoginId);
        User user = findUser(userId);
        validateReadableTarget(currentUser, user);
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse approve(Long userId, String currentLoginId) {
        User currentUser = findUserByLoginId(currentLoginId);
        User user = findUser(userId);
        validateWritableTarget(currentUser, user);
        user.setStatus("ACTIVE");
        user.setUseYn("Y");
        user.setUpdatedAt(LocalDateTime.now());
        departmentAuthorizationService.ensureDefaultAuthorization(user);
        return toAdminUserResponse(user);
    }
    @Transactional
    public AdminUserResponse updateStatus(
            Long userId,
            AdminUserStatusUpdateRequest request,
            String currentLoginId
    ) {
        User currentUser = findUserByLoginId(currentLoginId);
        User user = findUser(userId);
        validateWritableTarget(currentUser, user);
        user.setStatus(request.status());

        if (request.useYn() != null) {
            user.setUseYn(request.useYn());
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        departmentAuthorizationService.ensureDefaultAuthorization(user);
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateProfile(
            Long userId,
            AdminUserProfileUpdateRequest request,
            String currentLoginId
    ) {
        User currentUser = findUserByLoginId(currentLoginId);
        User user = findUser(userId);
        validateWritableTarget(currentUser, user);
        String nextDepartmentName = request.departmentName() != null
                ? normalizeText(request.departmentName())
                : normalizeText(user.getDepartmentName());
        String nextPositionName = request.positionName() != null
                ? normalizeText(request.positionName())
                : normalizeText(user.getPositionName());
        String nextJobRank = request.jobRank() != null
                ? normalizeText(request.jobRank())
                : normalizeText(user.getJobRank());

        validateDepartmentLeaderUniqueness(user.getUserId(), nextDepartmentName, nextPositionName, nextJobRank);

        if (request.departmentName() != null) {
            user.setDepartmentName(nextDepartmentName);
        }

        if (request.positionName() != null) {
            user.setPositionName(nextPositionName);
        }

        if (request.jobRank() != null) {              // ← 추가
            user.setJobRank(nextJobRank);
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        departmentAuthorizationService.ensureDefaultAuthorization(user);
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateRoles(Long userId, AdminUserRoleUpdateRequest request, String currentLoginId) {
        User currentUser = findUserByLoginId(currentLoginId);
        User user = findUser(userId);
        Set<Long> roleIds = new LinkedHashSet<>(request.roleIds());

        if (roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "역할을 하나 이상 선택해야 합니다.");
        }

        List<Role> roles = roleRepository.findByRoleIdInAndUseYnOrderBySortOrderAscRoleCodeAsc(roleIds, "Y");

        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효하지 않거나 비활성화된 역할이 포함되어 있습니다.");
        }

        boolean hasAdminRole = roles.stream()
                .anyMatch(role -> ROLE_ADMIN.equalsIgnoreCase(role.getRoleCode()));

        if (!hasAdminRole && isSameUser(user, currentLoginId) && hasRole(user.getUserId(), ROLE_ADMIN)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인 관리자 권한은 직접 해제할 수 없습니다.");
        }

        if (!isAdmin(currentUser)) {
            validateDelegatedRoleUpdate(currentUser, user, roles);
        }

        replaceUserRoles(userId, roles);
        user.setUpdatedAt(LocalDateTime.now());
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateDepartmentAuthorization(
            Long userId,
            AdminUserDepartmentAuthorizationUpdateRequest request,
            String currentLoginId
    ) {
        User currentUser = findUserByLoginId(currentLoginId);
        User user = findUser(userId);

        if (!isAdmin(currentUser)) {
            requireUserWritePermission(currentUser);
            requireScopedDepartment(currentUser);

            if (isSameUser(user, currentLoginId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot change your own department authorization.");
            }

            if (!isSameDepartment(currentUser, user)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Only users in your department can be changed.");
            }

            Set<String> targetRoleCodes = findRoleCodes(user.getUserId());
            if (targetRoleCodes.stream().anyMatch(PROTECTED_ROLE_CODES::contains)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Protected role holders cannot be changed.");
            }
        }

        departmentAuthorizationService.setAuthorized(user, Boolean.TRUE.equals(request.authorized()));
        user.setUpdatedAt(LocalDateTime.now());
        return toAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<String> findDepartments(String currentLoginId) {
        User currentUser = findUserByLoginId(currentLoginId);

        if (!isAdmin(currentUser)) {
            requireUserReadPermission(currentUser);
            return List.of(requireScopedDepartment(currentUser));
        }

        return userRepository.findDistinctDepartmentNames()
                .stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAssignableRoles(String currentLoginId) {
        User currentUser = findUserByLoginId(currentLoginId);

        if (isAdmin(currentUser)) {
            return roleRepository.findByUseYnOrderBySortOrderAscRoleCodeAsc("Y")
                    .stream()
                    .map(RoleResponse::from)
                    .toList();
        }

        requireUserReadPermission(currentUser);
        Set<String> roleCodes = grantableRoleCodesForDepartment(requireScopedDepartment(currentUser));
        if (roleCodes.isEmpty()) {
            return List.of();
        }

        return roleRepository.findByRoleCodeInAndUseYnOrderBySortOrderAscRoleCodeAsc(roleCodes, "Y")
                .stream()
                .map(RoleResponse::from)
                .toList();
    }

    private void replaceUserRoles(Long userId, List<Role> roles) {
        LocalDateTime now = LocalDateTime.now();
        userRoleRepository.deleteByUserId(userId);

        List<UserRole> userRoles = roles.stream()
                .map(role -> {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(role.getRoleId());
                    userRole.setCreatedAt(now);
                    return userRole;
                })
                .toList();

        userRoleRepository.saveAll(userRoles);
    }

    private boolean hasRole(Long userId, String roleCode) {
        return roleRepository.findByRoleCodeAndUseYn(roleCode, "Y")
                .map(role -> userRoleRepository.existsByUserIdAndRoleId(userId, role.getRoleId()))
                .orElse(false);
    }

    private Set<String> findRoleCodes(Long userId) {
        return new LinkedHashSet<>(rbacQueryService.findRoleCodesByUserId(userId));
    }

    private boolean isAdmin(User user) {
        return hasRole(user.getUserId(), ROLE_ADMIN);
    }

    private void validateReadableTarget(User currentUser, User targetUser) {
        if (isAdmin(currentUser)) {
            return;
        }

        requireUserReadPermission(currentUser);
        if (!isSameDepartment(currentUser, targetUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "자기 부서 사용자만 조회할 수 있습니다.");
        }
    }

    private void validateWritableTarget(User currentUser, User targetUser) {
        if (isAdmin(currentUser)) {
            return;
        }

        requireUserWritePermission(currentUser);
        if (!isSameDepartment(currentUser, targetUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "자기 부서 사용자만 수정할 수 있습니다.");
        }

        Set<String> targetRoleCodes = findRoleCodes(targetUser.getUserId());
        if (targetRoleCodes.stream().anyMatch(PROTECTED_ROLE_CODES::contains)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 또는 위임 관리자 역할 보유자는 수정할 수 없습니다.");
        }
    }

    private void validateDelegatedRoleUpdate(User currentUser, User targetUser, List<Role> requestedRoles) {
        requireUserWritePermission(currentUser);
        String departmentName = requireScopedDepartment(currentUser);

        if (isSameUser(targetUser, currentUser.getLoginId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 역할은 직접 변경할 수 없습니다.");
        }

        if (!isSameDepartment(currentUser, targetUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "자기 부서 사용자에게만 역할을 부여할 수 있습니다.");
        }

        Set<String> grantableRoleCodes = grantableRoleCodesForDepartment(departmentName);
        Set<String> requestedRoleCodes = requestedRoles.stream()
                .map(Role::getRoleCode)
                .map(this::normalizeRoleCode)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        if (requestedRoleCodes.stream().anyMatch(PROTECTED_ROLE_CODES::contains)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 또는 위임 관리자 역할은 부여할 수 없습니다.");
        }

        if (!grantableRoleCodes.containsAll(requestedRoleCodes)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 부서에서 부여할 수 없는 역할이 포함되어 있습니다.");
        }

        Set<String> existingRoleCodes = findRoleCodes(targetUser.getUserId());
        if (existingRoleCodes.stream().anyMatch(PROTECTED_ROLE_CODES::contains)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 또는 위임 관리자 역할 보유자는 수정할 수 없습니다.");
        }

        if (!grantableRoleCodes.containsAll(existingRoleCodes)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "현재 부서 위임 범위를 벗어난 기존 역할이 있습니다.");
        }
    }

    private User requireUserReadPermission(User currentUser) {
        if (isAdmin(currentUser) || hasAnyPermission(
                currentUser,
                PermissionCodes.USERS_READ,
                PermissionCodes.USERS_WRITE,
                PermissionCodes.LEGACY_USER_MANAGE
        )) {
            return currentUser;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN, "사용자 조회 권한이 필요합니다.");
    }

    private User requireUserWritePermission(User currentUser) {
        if (isAdmin(currentUser) || hasAnyPermission(
                currentUser,
                PermissionCodes.USERS_WRITE,
                PermissionCodes.LEGACY_USER_MANAGE
        )) {
            return currentUser;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN, "사용자 수정 권한이 필요합니다.");
    }

    private boolean hasAnyPermission(User user, String... permissionCodes) {
        Set<String> currentPermissionCodes = new LinkedHashSet<>(
                rbacQueryService.findPermissionCodesByUserId(user.getUserId())
        );

        for (String permissionCode : permissionCodes) {
            if (currentPermissionCodes.contains(permissionCode)) {
                return true;
            }
        }

        return false;
    }

    private String requireScopedDepartment(User currentUser) {
        String departmentName = normalizeText(currentUser.getDepartmentName());
        if (!StringUtils.hasText(departmentName)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "부서 정보가 없는 사용자는 부서 범위 사용자 관리를 할 수 없습니다.");
        }

        return departmentName;
    }

    private Set<String> grantableRoleCodesForDepartment(String departmentName) {
        String normalized = normalizeText(departmentName);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }

        return departmentRoleAssignmentRuleRepository.findRoleCodesByDepartmentName(normalized, "Y")
                .stream()
                .map(this::normalizeRoleCode)
                .filter(StringUtils::hasText)
                .filter(roleCode -> !PROTECTED_ROLE_CODES.contains(roleCode))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private boolean isSameDepartment(User left, User right) {
        String leftDepartment = normalizeText(left.getDepartmentName());
        String rightDepartment = normalizeText(right.getDepartmentName());
        return StringUtils.hasText(leftDepartment) && leftDepartment.equals(rightDepartment);
    }

    private void validateDepartmentLeaderUniqueness(
            Long userId,
            String departmentName,
            String positionName,
            String jobRank
    ) {
        if (!isLeaderTitle(positionName) && !isLeaderTitle(jobRank)) {
            return;
        }

        if (!StringUtils.hasText(departmentName)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "부서 팀장은 부서 정보가 필요합니다.");
        }

        long count = userRepository.countActiveDepartmentLeaders(
                departmentName,
                LEADER_KEYWORD,
                userId
        );

        if (count > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "해당 부서에는 이미 팀장 직급 사용자가 있습니다.");
        }
    }

    private boolean isLeaderTitle(String value) {
        return StringUtils.hasText(value) && value.contains(LEADER_KEYWORD);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private User findUserByLoginId(String loginId) {
        if (!StringUtils.hasText(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        List<Long> roleIds = userRoleRepository.findByUserId(user.getUserId())
                .stream()
                .map(UserRole::getRoleId)
                .toList();

        List<RoleResponse> roles = roleIds.isEmpty()
                ? List.of()
                : roleRepository.findByRoleIdInAndUseYnOrderBySortOrderAscRoleCodeAsc(roleIds, "Y")
                        .stream()
                        .map(RoleResponse::from)
                        .toList();

        return new AdminUserResponse(
                UserResponse.from(user),
                roles,
                rbacQueryService.findPermissionCodesByUserId(user.getUserId()),
                departmentAuthorizationService.isAuthorized(user)
        );
    }

    private boolean isSameUser(User user, String currentLoginId) {
        return StringUtils.hasText(currentLoginId) && currentLoginId.equals(user.getLoginId());
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String normalizeRoleCode(String roleCode) {
        String normalized = normalizeText(roleCode);
        if (normalized == null) {
            return null;
        }

        return normalized.replaceFirst("^ROLE_", "").toUpperCase();
    }

    private String normalizeDepartmentAuthorized(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }

        if ("Y".equalsIgnoreCase(normalized) || "TRUE".equalsIgnoreCase(normalized)) {
            return "Y";
        }

        if ("N".equalsIgnoreCase(normalized) || "FALSE".equalsIgnoreCase(normalized)) {
            return "N";
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST, "부서원 자격 검색값은 Y 또는 N만 사용할 수 있습니다.");
    }

    private String normalizeJobRankFilter(String jobRank) {
        return normalizeText(jobRank);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }

        return Math.min(size, 100);
    }
}
