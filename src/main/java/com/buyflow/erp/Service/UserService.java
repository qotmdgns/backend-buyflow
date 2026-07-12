package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.UserResponse;
import com.buyflow.erp.Dto.UserUpdateRequest;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String JOB_RANK_ADMIN = "ADMIN";
    private static final String JOB_RANK_USER = "USER";

    private final AuthUserRepository userRepository;

    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    public PageResponse<UserResponse> search(
            String keyword,
            String status,
            String useYn,
            String jobRank,
            int page,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return PageResponse.from(userRepository.search(
                normalizeText(keyword),
                null,
                normalizeText(status),
                normalizeText(useYn),
                normalizeJobRankFilter(jobRank),
                null,
                null,
                pageRequest
        ).map(UserResponse::from));
    }

    public UserResponse findById(Long userId, String currentLoginId, boolean isAdmin) {
        User target = findUser(userId);
        User currentUser = findCurrentUser(currentLoginId);

        requireSelfOrAdmin(target, currentUser, isAdmin);
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse update(Long userId, UserUpdateRequest request, String currentLoginId, boolean isAdmin) {
        User target = findUser(userId);
        User currentUser = findCurrentUser(currentLoginId);

        requireSelfOrAdmin(target, currentUser, isAdmin);

        if (!isAdmin && StringUtils.hasText(request.jobRank())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (StringUtils.hasText(request.userName())) {
            target.setUserName(request.userName().trim());
        }

        if (request.email() != null) {
            target.setEmail(normalizeText(request.email()));
        }

        if (request.phone() != null) {
            target.setPhone(normalizeText(request.phone()));
        }

        if (isAdmin) {
            if (request.departmentName() != null) {
                target.setDepartmentName(normalizeText(request.departmentName()));
            }

            if (request.positionName() != null) {
                target.setPositionName(normalizeText(request.positionName()));
            }

            
            
        }

        target.setUpdatedAt(LocalDateTime.now());
        return UserResponse.from(target);
    }

    @Transactional
    public void deactivate(Long userId) {
        User target = findUser(userId);

        target.setStatus("INACTIVE");
        target.setUseYn("N");
        target.setUpdatedAt(LocalDateTime.now());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private User findCurrentUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void requireSelfOrAdmin(User target, User currentUser, boolean isAdmin) {
        if (!isAdmin && !target.getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String normalizeJobRank(String jobRank) {
        if (!StringUtils.hasText(jobRank)) {
            return JOB_RANK_USER;
        }

        return JOB_RANK_ADMIN.equalsIgnoreCase(jobRank.trim()) ? JOB_RANK_ADMIN : JOB_RANK_USER;
    }

    private String normalizeJobRankFilter(String jobRank) {
        if (!StringUtils.hasText(jobRank)) {
            return null;
        }

        return normalizeJobRank(jobRank);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }

        return Math.min(size, 100);
    }
}
