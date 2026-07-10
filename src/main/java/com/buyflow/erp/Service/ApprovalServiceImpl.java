package com.buyflow.erp.Service;

import com.buyflow.erp.Dto.ApprovalHistoryDto;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Entity.ApprovalHistory;
import com.buyflow.erp.Entity.Product;
import com.buyflow.erp.Entity.PurchaseRequest;
import com.buyflow.erp.Entity.PurchaseRequestItem;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.ApprovalHistoryRepository;
import com.buyflow.erp.Repository.AttachmentRepository;
import com.buyflow.erp.Repository.ProductRepository;
import com.buyflow.erp.Repository.PurchaseRequestItemRepository;
import com.buyflow.erp.Repository.PurchaseRequestRepository;
import com.buyflow.erp.Repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalServiceImpl implements ApprovalService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;

    @Override
    public PageResponse<ApprovalHistoryDto.ListResponse> getApprovals(
            String requestNumber,
            String title,
            String requester,
            String department,
            String status,
            String desiredReceiptAt,
            int page,
            int size
    ) {
            int safePage = Math.max(page, 0);
            int safeSize = Math.max(size, 1);

            Long currentUserId = getCurrentLoginUserId();
            boolean canViewAllApprovals = canReadAllApprovals(currentUserId);

            List<ApprovalHistoryDto.ListResponse> filtered =
                    approvalHistoryRepository.findAllByOrderByApprovalIdDesc()
                            .stream()
                            .filter(approval ->
                            canViewAllApprovals
                                    || isVisibleApprovalForCurrentUser(approval, currentUserId)
)
                            .map(this::toListResponse)
                            .filter(Objects::nonNull)
                            .filter(row -> contains(row.requestNumber(), requestNumber))
                            .filter(row -> contains(row.title(), title))
                            .filter(row -> contains(row.requester(), requester))
                            .filter(row -> contains(row.department(), department))
                            .filter(row -> isBlank(status)
                                    || "전체".equals(status)
                                    || Objects.equals(row.requestStatus(), status)
                                    || Objects.equals(row.requestStatusLabel(), status))
                            .filter(row -> isSameDate(row.desiredReceiptAt(), desiredReceiptAt))
                            .toList();
            long totalElements = filtered.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / safeSize));
            int fromIndex = Math.min(safePage * safeSize, filtered.size());
            int toIndex = Math.min(fromIndex + safeSize, filtered.size());

            return new PageResponse<>(
                    filtered.subList(fromIndex, toIndex),
                    new PageResponse.Pagination(safePage + 1, safeSize, totalElements, totalPages)
            );
        }

    @Override
    public ApprovalHistoryDto.DetailResponse getApprovalDetail(Long approvalId) {
        ApprovalHistory approval = findApproval(approvalId);

        validateCanReadApproval(approval);

        PurchaseRequest request = findRequest(approval.getRequestId());

        return toDetailResponse(approval, request);
}

    @Override
    @Transactional
    public ApprovalHistoryDto.DetailResponse approve(Long approvalId, ApprovalHistoryDto.DecisionRequest dto) {
    ApprovalHistory approval = findApproval(approvalId);

    validateCanProcessApproval(approval);

    PurchaseRequest request = findRequest(approval.getRequestId());

    validatePendingApproval(approval, request);

    approval.setApprovalStatus("APPROVED");
    approval.setCommentText(dto != null ? dto.comment() : null);
    approval.setApprovedAt(LocalDateTime.now());

    request.setRequestStatus("APPROVED");
    request.setUpdatedAt(LocalDateTime.now());

    approvalHistoryRepository.save(approval);
    purchaseRequestRepository.save(request);

    return toDetailResponse(approval, request);
}

    @Override
    @Transactional
    public ApprovalHistoryDto.DetailResponse reject(Long approvalId, ApprovalHistoryDto.DecisionRequest dto) {
        ApprovalHistory approval = findApproval(approvalId);

        validateCanProcessApproval(approval);
        validateRejectComment(dto);

        PurchaseRequest request = findRequest(approval.getRequestId());

        validatePendingApproval(approval, request);

        approval.setApprovalStatus("REJECTED");
        approval.setCommentText(dto.comment().trim());
        approval.setApprovedAt(LocalDateTime.now());

        request.setRequestStatus("REJECTED");
        request.setUpdatedAt(LocalDateTime.now());

        approvalHistoryRepository.save(approval);
        purchaseRequestRepository.save(request);

        return toDetailResponse(approval, request);
}

    @Override
    @Transactional
    public ApprovalHistoryDto.DetailResponse cancelRequest(Long approvalId) {
        ApprovalHistory approval = findApproval(approvalId);

        validateCanProcessApproval(approval);

        PurchaseRequest request = findRequest(approval.getRequestId());

        validatePendingApproval(approval, request);

        LocalDateTime now = LocalDateTime.now();

        approval.setApprovalStatus("CANCELED");
        approval.setCommentText("요청 취소");
        approval.setApprovedAt(now);

        request.setRequestStatus("CANCELED");
        request.setUpdatedAt(now);

        approvalHistoryRepository.save(approval);
        purchaseRequestRepository.save(request);

        return toDetailResponse(approval, request);
}

    private ApprovalHistoryDto.ListResponse toListResponse(ApprovalHistory approval) {
        PurchaseRequest request = purchaseRequestRepository.findById(approval.getRequestId()).orElse(null);
        if (request == null || "Y".equalsIgnoreCase(nullToEmpty(request.getDeletedYn()).trim())) {
            return null;
        }

        return new ApprovalHistoryDto.ListResponse(
        approval.getApprovalId(),
        request.getRequestId(),
        nullToEmpty(request.getRequestNo()),
        nullToEmpty(request.getTitle()),
        getUserName(request.getRequestorId()),
        getDepartmentName(request.getRequestorId()),
        formatDate(request.getCreatedAt()),
        formatDate(request.getDueDate()),
        formatDateTime(request.getCreatedAt()),
        formatDateTime(request.getUpdatedAt()),
        request.getTotalAmount() != null ? request.getTotalAmount() : calculateTotalAmount(request.getRequestId()),
        resolvePriorityLabel(request),
        toStatusCode(request.getRequestStatus()),
        toRequestStatusLabel(request.getRequestStatus()),
        createStepLabel(approval),
        getApproverName(approval.getApproverId())
);
    }

    private ApprovalHistoryDto.DetailResponse toDetailResponse(
        ApprovalHistory approval,
        PurchaseRequest request
) {
    Users requester = request.getRequestorId() == null
            ? null
            : userRepository.findById(request.getRequestorId()).orElse(null);

    Users approver = approval.getApproverId() == null
            ? null
            : userRepository.findById(approval.getApproverId()).orElse(null);

    Long currentUserId = getCurrentLoginUserId();

    boolean canProcess = canProcessCurrentApproval(
            approval,
            request,
            currentUserId
    );

    return new ApprovalHistoryDto.DetailResponse(
            approval.getApprovalId(),
            request.getRequestId(),
            nullToEmpty(request.getRequestNo()),
            nullToEmpty(request.getTitle()),
            new ApprovalHistoryDto.UserInfo(
                    requester != null ? requester.getUserId() : request.getRequestorId(),
                    requester != null ? nullToEmpty(requester.getUserName()) : getUserName(request.getRequestorId()),
                    ""
            ),
            new ApprovalHistoryDto.DepartmentInfo(
                    null,
                    getDepartmentName(request.getRequestorId())
            ),
            formatDate(request.getCreatedAt()),
            formatDate(request.getDueDate()),
            formatDateTime(request.getCreatedAt()),
            formatDateTime(request.getUpdatedAt()),
            resolvePriorityLabel(request),
            toStatusCode(request.getRequestStatus()),
            toRequestStatusLabel(request.getRequestStatus()),
            nullToEmpty(request.getReason()),
            canProcess,
            getApprovalItemResponses(request.getRequestId()),
            getAttachmentResponses(request.getRequestId()),
            new ApprovalHistoryDto.CurrentStep(
                    createStepLabel(approval),
                    new ApprovalHistoryDto.UserInfo(
                            approver != null ? approver.getUserId() : approval.getApproverId(),
                            approver != null ? nullToEmpty(approver.getUserName()) : getApproverName(approval.getApproverId()),
                            ""
                    )
            ),
            getHistoryResponses(request.getRequestId())
    );
}

    private List<ApprovalHistoryDto.ApprovalItemResponse> getApprovalItemResponses(Long requestId) {
        List<PurchaseRequestItem> items = purchaseRequestItemRepository.findByRequestIdOrderByRequestItemIdAsc(requestId);
        Map<Long, Product> productMap = productRepository.findAllById(
                        items.stream()
                                .map(PurchaseRequestItem::getProductId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                )
                .stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));

        return items.stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    int quantity = item.getRequestQuantity() != null ? item.getRequestQuantity() : 0;
                    BigDecimal unitPrice = item.getEstimatedUnitPrice() != null ? item.getEstimatedUnitPrice() : BigDecimal.ZERO;


            return new ApprovalHistoryDto.ApprovalItemResponse(
                item.getRequestItemId(),
                item.getProductId(),
                product != null ? nullToEmpty(product.getProductNo()) : "",
                product != null ? nullToEmpty(product.getProductName()) : "",
                product != null ? nullToEmpty(product.getCategoryName()) : "",
                product != null ? nullToEmpty(product.getSpec()) : "",
                quantity,
                product != null ? nullToEmpty(product.getUnit()) : "",
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                "해당 사항 없음",
                product != null ? formatDateTime(product.getCreatedAt()) : "",
                product != null ? formatDateTime(product.getUpdatedAt()) : ""
        );
                })
                .toList();
    }

    private List<ApprovalHistoryDto.HistoryResponse> getHistoryResponses(Long requestId) {
        return approvalHistoryRepository.findByRequestIdOrderByApprovalStepAsc(requestId)
                .stream()
                .map(history -> new ApprovalHistoryDto.HistoryResponse(
                        history.getApprovalId(),
                        toHistoryStatus(history.getApprovalStatus()),
                        createHistoryTitle(history.getApprovalStatus()),
                        getApproverName(history.getApproverId()),
                        "",
                        formatDateTime(history.getApprovedAt()),
                        isBlank(history.getCommentText()) ? toRequestStatusLabel(history.getApprovalStatus()) : history.getCommentText()
                ))
                .toList();
    }

    private ApprovalHistory findApproval(Long approvalId) {
        return approvalHistoryRepository.findById(approvalId)
                .orElseThrow(() -> new EntityNotFoundException("승인 이력을 찾을 수 없습니다. ID: " + approvalId));
    }

    private PurchaseRequest findRequest(Long requestId) {
        return purchaseRequestRepository.findById(requestId)
                .filter(request -> !"Y".equalsIgnoreCase(nullToEmpty(request.getDeletedYn()).trim()))
                .orElseThrow(() -> new EntityNotFoundException("구매 요청을 찾을 수 없습니다. ID: " + requestId));
    }

    private BigDecimal calculateTotalAmount(Long requestId) {
        return purchaseRequestItemRepository.findByRequestIdOrderByRequestItemIdAsc(requestId)
                .stream()
                .map(item -> {
                    int quantity = item.getRequestQuantity() != null ? item.getRequestQuantity() : 0;
                    BigDecimal unitPrice = item.getEstimatedUnitPrice() != null
                            ? item.getEstimatedUnitPrice()
                            : BigDecimal.ZERO;
                    return unitPrice.multiply(BigDecimal.valueOf(quantity));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String getUserName(Long userId) {
        if (userId == null) {
            return "-";
        }
        return userRepository.findById(userId)
                .map(Users::getUserName)
                .filter(name -> !isBlank(name))
                .orElse("사용자 " + userId);
    }

    private String getApproverName(Long approverId) {
        if (approverId == null) {
            return "-";
        }
        return userRepository.findById(approverId)
                .map(Users::getUserName)
                .filter(name -> !isBlank(name))
                .orElse("사용자 " + approverId);
    }

    private String createStepLabel(ApprovalHistory approval) {
        if (approval.getApprovalStep() == null) {
            return "승인 단계";
        }
        return approval.getApprovalStep() + "차 승인";
    }

    private String createHistoryTitle(String status) {
        return switch (toStatusCode(status)) {
            case "APPROVED" -> "승인 완료";
            case "REJECTED" -> "승인 반려";
            case "CANCEL_REQUESTED" -> "요청 취소";
            default -> "승인 검토 중";
        };
    }

    private String toHistoryStatus(String status) {
        String code = toStatusCode(status);
        if ("APPROVED".equals(code) || "REJECTED".equals(code)) {
            return "DONE";
        }
        return "CURRENT";
    }

    private String toStatusCode(String status) {
        if (status == null) {
            return "PENDING_APPROVAL";
        }
        return switch (status.trim().toUpperCase()) {
            case "DRAFT" -> "DRAFT";
            case "PENDING", "PENDING_APPROVAL", "WAITING", "REQUESTED" -> "PENDING_APPROVAL";
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "ORDERED" -> "ORDERED";
            case "CANCEL_REQUESTED", "CANCELED", "CANCELLED" -> "CANCEL_REQUESTED";
            default -> status;
        };
    }

    private String toRequestStatusLabel(String status) {
        return switch (toStatusCode(status)) {
            case "DRAFT" -> "임시 저장";
            case "PENDING_APPROVAL" -> "승인 대기";
            case "APPROVED" -> "승인 완료";
            case "REJECTED" -> "승인 반려";
            case "ORDERED" -> "발주 완료";
            case "CANCEL_REQUESTED" -> "요청 취소";
            default -> status == null ? "승인 대기" : status;
        };
    }

    private String resolvePriorityLabel(PurchaseRequest request) {
        if (request.getDueDate() != null && !request.getDueDate().isAfter(LocalDate.now().plusDays(3))) {
            return "긴급";
        }
        return "일반";
    }

    private boolean contains(String value, String keyword) {
        return isBlank(keyword) || nullToEmpty(value).toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private boolean isSameDate(String value, String date) {
    if (isBlank(date)) {
        return true;
    }

    return nullToEmpty(value).startsWith(date);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.toLocalDate().format(DATE_FORMATTER);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
    return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private String getDepartmentName(Long userId) {
    if (userId == null) {
        return "-";
    }

    return userRepository.findById(userId)
            .map(Users::getDepartmentName)
            .filter(name -> !isBlank(name))
            .orElse("-");
    }
    
    private boolean hasRole(Long userId, String roleCode) {
        if (userId == null || isBlank(roleCode)) {
            return false;
        }

        return userRepository.countActiveRoleByUserId(userId, roleCode) > 0;
    }

    private boolean hasPermission(Long userId, String permissionCode) {
        if (userId == null || isBlank(permissionCode)) {
            return false;
        }

        return userRepository.countActivePermissionByUserId(userId, permissionCode) > 0;
    }

    private boolean hasAnyRole(Long userId, String... roleCodes) {
        if (userId == null || roleCodes == null) {
            return false;
        }

        for (String roleCode : roleCodes) {
            if (hasRole(userId, roleCode)) {
                return true;
            }
        }

        return false;
    }

    private boolean canReadApprovalManagement(Long userId) {
        return hasPermission(userId, "approvals.read")
                || hasPermission(userId, "approvals.process")
                || hasAnyRole(userId, "ADMIN", "MANAGER", "APPROVER", "TEAM_MANAGER");
    }

    private boolean canProcessApproval(Long userId) {
        return hasPermission(userId, "approvals.process")
                || hasAnyRole(userId, "ADMIN", "MANAGER", "APPROVER");
}

    private boolean canReadAllApprovals(Long userId) {
        return hasPermission(userId, "approvals.read")
                || hasAnyRole(userId, "ADMIN", "MANAGER", "TEAM_MANAGER");
}

    private void validateCanReadApproval(ApprovalHistory approval) {
    Long currentUserId = getCurrentLoginUserId();

    if (canReadApprovalManagement(currentUserId)) {
        return;
    }

    if (isVisibleApprovalForCurrentUser(approval, currentUserId)) {
        return;
    }

    validateCurrentApprover(approval, currentUserId);
}

    private void validateCanProcessApproval(ApprovalHistory approval) {
        Long currentUserId = getCurrentLoginUserId();

        if (!canProcessApproval(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "승인 처리 권한이 없습니다."
        );
    }

        validateCurrentApprover(approval, currentUserId);
    }

    private void validateCurrentApprover(ApprovalHistory approval) {
        validateCurrentApprover(approval, getCurrentLoginUserId());
    }

    private void validateCurrentApprover(ApprovalHistory approval, Long currentUserId) {
        Long approverId = approval.getApproverId();

        if (approverId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "승인자가 지정되지 않은 승인 건입니다."
            );
        }

        if (!approverId.equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 승인 건의 승인자만 처리할 수 있습니다."
            );
        }
    }

    
    private Long getCurrentLoginUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null
            || !authentication.isAuthenticated()
            || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "로그인 사용자 정보를 확인할 수 없습니다.");
        }
        
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof Users user) {
            return user.getUserId();
        }
        
        if (principal instanceof UserDetails userDetails) {
            return resolveLoginUserId(userDetails.getUsername());
        }
        
        return resolveLoginUserId(authentication.getName());
    }

    private Long resolveLoginUserId(String value) {
        if (isBlank(value)) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "로그인 사용자 정보를 확인할 수 없습니다.");
        }

        return userRepository.findByLoginId(value.trim())
            .map(Users::getUserId)
            .orElseGet(() -> parseUserId(value));
    }
    
    private Long parseUserId(String value) {
    if (isBlank(value)) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "로그인 사용자 정보를 확인할 수 없습니다.");
    }

    String loginValue = value.trim();

    try {
        return Long.valueOf(loginValue);
    } catch (NumberFormatException ignored) {
        return userRepository.findByLoginId(loginValue)
            .map(Users::getUserId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "현재 로그인 사용자를 찾을 수 없습니다."
            ));
    }
}
    
    private void validateRejectComment(ApprovalHistoryDto.DecisionRequest dto) {
        if (dto == null || dto.comment() == null || dto.comment().trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "반려 사유는 필수입니다."
            );
        }
    }

        private void validatePendingApproval(ApprovalHistory approval, PurchaseRequest request) {
            String requestStatus = toStatusCode(request.getRequestStatus());

            if (!"PENDING_APPROVAL".equals(requestStatus)) {
                throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "승인 대기 상태의 요청만 처리할 수 있습니다. 현재 상태: "
                        + toRequestStatusLabel(requestStatus)
            );
        }

        String approvalStatus = toStatusCode(approval.getApprovalStatus());

            if (!"PENDING_APPROVAL".equals(approvalStatus)) {
                throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "이미 처리된 승인 건입니다. 현재 승인 상태: "
                        + toRequestStatusLabel(approvalStatus)
            );
        }
    }
    
    private List<ApprovalHistoryDto.AttachmentResponse> getAttachmentResponses(Long requestId) {
        return attachmentRepository.findByRequestIdOrderByAttachmentIdAsc(requestId)
            .stream()
            .map(attachment -> new ApprovalHistoryDto.AttachmentResponse(
                    attachment.getAttachmentId(),
                    attachment.getOriginalName(),
                    "/api/purchase-requests/attachments/"
                            + attachment.getAttachmentId()
                            + "/download"
            ))
                .toList();
    }

    @Override
    public ApprovalHistoryDto.SummaryResponse getApprovalSummary() {
        Long currentUserId = getCurrentLoginUserId();
        boolean canViewAllApprovals = canReadAllApprovals(currentUserId);

     List<ApprovalHistoryDto.ListResponse> rows =
            approvalHistoryRepository.findAllByOrderByApprovalIdDesc()
                    .stream()
                    .filter(approval ->
                    canViewAllApprovals
                            || isVisibleApprovalForCurrentUser(approval, currentUserId)
)
                    .map(this::toListResponse)
                    .filter(Objects::nonNull)
                    .toList();

     return new ApprovalHistoryDto.SummaryResponse(
            rows.size(),
            countApprovalRowsByStatus(rows, "PENDING_APPROVAL"),
            countApprovalRowsByStatus(rows, "REJECTED"),
            countApprovalRowsByStatus(rows, "APPROVED")
    );
}

    private boolean isVisibleApprovalForCurrentUser(
        ApprovalHistory approval,
        Long currentUserId
) {
    if (approval == null || currentUserId == null) {
        return false;
    }


    if (Objects.equals(approval.getApproverId(), currentUserId)) {
        return true;
    }



    if (!canProcessApproval(currentUserId)) {
        return false;
    }



    PurchaseRequest request = purchaseRequestRepository
            .findById(approval.getRequestId())
            .orElse(null);

    return request != null
            && !"Y".equalsIgnoreCase(nullToEmpty(request.getDeletedYn()).trim())
            && Objects.equals(request.getRequestorId(), currentUserId);
}

    private long countApprovalRowsByStatus(
        List<ApprovalHistoryDto.ListResponse> rows,
        String statusCode
) {
     return rows.stream()
            .filter(row -> statusCode.equals(toStatusCode(row.requestStatus())))
            .count();
}

    private boolean canProcessCurrentApproval(
        ApprovalHistory approval,
        PurchaseRequest request,
        Long currentUserId
) {
    return approval != null
            && request != null
            && currentUserId != null
            && canProcessApproval(currentUserId)
            && Objects.equals(approval.getApproverId(), currentUserId)
            && "PENDING_APPROVAL".equals(toStatusCode(approval.getApprovalStatus()))
            && "PENDING_APPROVAL".equals(toStatusCode(request.getRequestStatus()));
}

}

