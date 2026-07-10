package com.buyflow.erp.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.PurchaseRequestDto;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseRequestServiceImpl implements PurchaseRequestService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIXED_ITEM_REMARK = "해당 사항 없음";

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileService fileService;

    @Override
    public PageResponse<PurchaseRequestDto.ListResponse> getPurchaseRequests(
            String requestNumber,
            String title,
            String requester,
            String department,
            String status,
            String priority,
            String desiredReceiptAt,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        String statusFilter = toRequestStatusLabel(status);

        List<PurchaseRequestDto.ListResponse> filtered = purchaseRequestRepository.findActiveRequestsOrderByRequestIdDesc()
                .stream()
                .map(this::toListResponse)
                .filter(row -> contains(row.requestNumber(), requestNumber))
                .filter(row -> contains(row.title(), title))
                .filter(row -> contains(row.requester(), requester))
                .filter(row -> isAllOrEquals(department, "전체 부서", row.department()))
                .filter(row -> isAllOrEquals(statusFilter, "전체", row.status()))
                .filter(row -> isAllOrEquals(priority, "전체", row.priority()))
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
    public PurchaseRequestDto.DetailResponse getPurchaseRequestDetail(Long requestId) {
        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .filter(this::isActive)
                .orElseThrow(() -> new EntityNotFoundException("구매 요청을 찾을 수 없습니다. ID: " + requestId));

List<PurchaseRequestDto.ItemResponse> items = getItemResponses(requestId);
BigDecimal calculatedTotalAmount = items.stream()
        .map(PurchaseRequestDto.ItemResponse::estimatedAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new PurchaseRequestDto.DetailResponse(
                request.getRequestId(),
                nullToEmpty(request.getRequestNo()),
                nullToEmpty(request.getTitle()),
                getUserName(request.getRequestorId()),
                getDepartmentName(request.getRequestorId()),
                formatDate(request.getCreatedAt()),
                formatDate(request.getDueDate()),
                formatDateTime(request.getCreatedAt()),
                formatDateTime(request.getUpdatedAt()),
                resolvePriorityLabel(request),
                toRequestStatusLabel(request.getRequestStatus()),
                nullToEmpty(request.getReason()),
                request.getTotalAmount() != null ? request.getTotalAmount() : calculatedTotalAmount,
                items,
                getAttachmentResponses(requestId)
                );
         }

    @Override
    public PurchaseRequestDto.SummaryResponse getPurchaseRequestSummary() {
        List<PurchaseRequest> requests =
            purchaseRequestRepository.findActiveRequestsOrderByRequestIdDesc();

        return new PurchaseRequestDto.SummaryResponse(
            requests.size(),
            countByPriorityLabel(requests, "일반"),
            countByPriorityLabel(requests, "긴급"),
            countByStatusLabel(requests, "요청 취소")
    );
}

    private long countByPriorityLabel(List<PurchaseRequest> requests, String priorityLabel) {
        return requests.stream()
                .filter(request -> priorityLabel.equals(resolvePriorityLabel(request)))
                .count();
    }

    @Override
    public Map<String, Object> getFilterOptions() {
        Set<String> departments = new LinkedHashSet<>();
            departments.add("전체 부서");

            userRepository.findAll().stream()
                .map(Users::getDepartmentName)
                .filter(name -> !isBlank(name))
                .forEach(departments::add);

        if (departments.size() == 1) {
                departments.add("-");
         }

        Set<String> statuses = new LinkedHashSet<>(
        List.of("전체", "승인 대기", "승인 완료", "반려", "발주 완료", "요청 취소")
        );
        Set<String> priorities = new LinkedHashSet<>(List.of("전체", "일반", "긴급"));

        return Map.of(
                "departments", List.copyOf(departments),
                "statuses", List.copyOf(statuses),
                "priorities", List.copyOf(priorities)
        );
    }

    @Override
    @Transactional
    public PurchaseRequestDto.DetailResponse createPurchaseRequest(
        PurchaseRequestDto.CreateRequest dto,
        MultipartFile file
    ) {
        LocalDateTime now = LocalDateTime.now();

        validateCreateRequestDateIsToday(dto.requestDate());

        PurchaseRequest request = new PurchaseRequest();
        Long requestorId = dto.requestorId();

    if (requestorId == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
            "요청자 ID는 필수입니다."
    );
}

        if (!userRepository.existsById(requestorId)) {
            throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "존재하지 않는 요청자 ID입니다: " + requestorId
            );
        }

        request.setRequestorId(requestorId);
        request.setTitle(nullToEmpty(dto.title()));
        request.setReason(nullToEmpty(dto.reason()));
        request.setDueDate(parseRequiredDate(dto.expectedDate(), "희망 입고일"));
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setRequestStatus(normalizeRequestStatus(dto.status()));

        request.setPriority(normalizePriority(dto.priority(), dto.urgency()));

        request.setDeletedYn("N");

        request.setTotalAmount(BigDecimal.ZERO);

        /*
        * REQUEST_NO는 DB에서 NOT NULL 컬럼이다.
        * 따라서 첫 save 전에 임시 요청번호라도 반드시 넣어야 한다.
        */
        boolean autoGenerateRequestNo = isBlank(dto.requestNumber());

        request.setRequestNo(
            autoGenerateRequestNo
                ? "PR-TEMP-" + System.currentTimeMillis()
                : dto.requestNumber().trim()
    );

        purchaseRequestRepository.save(request);

        Long requestId = request.getRequestId();

        if (autoGenerateRequestNo) {
            request.setRequestNo(createRequestNumber(requestId));
        }

        List<PurchaseRequestDto.CreateItemRequest> itemDtos =
        dto.items() == null ? List.of() : dto.items();

            if (itemDtos.isEmpty()) {
            throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "구매 요청 품목은 1개 이상 필요합니다."
        );
    }

        List<PurchaseRequestItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PurchaseRequestDto.CreateItemRequest itemDto : itemDtos) {
    if (itemDto.productId() == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "품목 ID는 필수입니다."
        );
    }

        Product product = productRepository.findById(itemDto.productId())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "존재하지 않는 품목 ID입니다: " + itemDto.productId()
            ));

            int quantity = itemDto.requestQuantity() != null
            ? itemDto.requestQuantity()
            : 0;

            if (quantity <= 0) {
                throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "요청 수량은 1개 이상이어야 합니다."
        );
    }

        BigDecimal unitPrice = itemDto.estimatedUnitPrice() != null
            ? itemDto.estimatedUnitPrice()
            : toBigDecimal(product.getUnitPrice());

            totalAmount = totalAmount.add(calculateAmount(quantity, unitPrice));

        PurchaseRequestItem item = new PurchaseRequestItem();
            item.setRequestId(requestId);
            item.setProductId(itemDto.productId());
            item.setRequestQuantity(quantity);
            item.setEstimatedUnitPrice(unitPrice);
            item.setRemark(FIXED_ITEM_REMARK);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);

            items.add(item);
    }

		    request.setTotalAmount(totalAmount);
		    purchaseRequestRepository.save(request);
		    purchaseRequestItemRepository.saveAll(items);
		
		    if ("PENDING_APPROVAL".equals(normalizeRequestStatus(request.getRequestStatus()))) {
            ApprovalHistory approvalHistory = new ApprovalHistory();

            approvalHistory.setRequestId(requestId);
            approvalHistory.setApproverId(resolveApproverId(dto.approverId(), requestorId));
            approvalHistory.setApprovalStatus("PENDING_APPROVAL");
            approvalHistory.setCommentText("구매 요청 승인 대기");
            approvalHistory.setApprovedAt(null);
            approvalHistory.setApprovalStep(1);

            approvalHistoryRepository.save(approvalHistory);
        }

        try {
                if (file != null && !file.isEmpty()) {
                fileService.uploadFile(
                file,
                requestorId,
                getUserName(requestorId),
                requestId
        );
      }
    } catch (Exception error) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "첨부파일 저장에 실패했습니다.",
            error
    );
}

		    return getPurchaseRequestDetail(requestId);
}

    @Transactional
    public PurchaseRequestDto.DetailResponse updatePurchaseRequest(
        Long requestId,
        PurchaseRequestDto.UpdateRequest dto,
        MultipartFile file
    ) {
        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .filter(this::isActive)
                .orElseThrow(() -> new EntityNotFoundException(
                    "구매 요청을 찾을 수 없습니다. ID: " + requestId
            ));

        validateEditableStatus(request);
        validateUpdateRequestDateMatchesCreatedAt(dto.requestDate(), request.getCreatedAt());

    if (isBlank(dto.title())) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "요청 제목은 필수입니다."
        );
    }

    if (isBlank(dto.reason())) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "요청 사유는 필수입니다."
        );
    }

    List<PurchaseRequestDto.UpdateItemRequest> itemDtos =
            dto.items() == null ? List.of() : dto.items();

    if (itemDtos.isEmpty()) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "구매 요청 품목은 1개 이상 필요합니다."
        );
    }

    LocalDateTime now = LocalDateTime.now();

    request.setTitle(dto.title().trim());
    request.setReason(dto.reason().trim());
    request.setDueDate(parseRequiredDate(dto.expectedDate(), "희망 입고일"));
    request.setUpdatedAt(now);

    List<PurchaseRequestItem> existingItems =
            purchaseRequestItemRepository.findByRequestIdOrderByRequestItemIdAsc(requestId);

    purchaseRequestItemRepository.deleteAll(existingItems);

    List<PurchaseRequestItem> nextItems = new ArrayList<>();
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (PurchaseRequestDto.UpdateItemRequest itemDto : itemDtos) {
        if (itemDto.productId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "품목 ID는 필수입니다."
            );
        }

        Product product = productRepository.findById(itemDto.productId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "존재하지 않는 품목 ID입니다: " + itemDto.productId()
                ));

        int quantity = itemDto.requestQuantity() != null
                ? itemDto.requestQuantity()
                : 0;

        if (quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청 수량은 1개 이상이어야 합니다."
            );
        }

        BigDecimal unitPrice = itemDto.estimatedUnitPrice() != null
                ? itemDto.estimatedUnitPrice()
                : toBigDecimal(product.getUnitPrice());

        BigDecimal amount = calculateAmount(quantity, unitPrice);
        totalAmount = totalAmount.add(amount);

        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setRequestId(requestId);
        item.setProductId(itemDto.productId());
        item.setRequestQuantity(quantity);
        item.setEstimatedUnitPrice(unitPrice);
        item.setRemark(FIXED_ITEM_REMARK);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        nextItems.add(item);
    }

    request.setTotalAmount(totalAmount);

    purchaseRequestRepository.save(request);
    purchaseRequestItemRepository.saveAll(nextItems);

try {
    if (file != null && !file.isEmpty()) {
        fileService.uploadFile(
                file,
                request.getRequestorId(),
                getUserName(request.getRequestorId()),
                requestId
        );
    }
} catch (Exception error) {
    throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "첨부파일 저장에 실패했습니다.",
            error
    );
}

return getPurchaseRequestDetail(requestId);
}

@Override
@Transactional
public PurchaseRequestDto.DetailResponse cancelPurchaseRequest(Long requestId) {
    PurchaseRequest request = purchaseRequestRepository.findById(requestId)
            .filter(this::isActive)
            .orElseThrow(() -> new EntityNotFoundException(
                    "구매 요청을 찾을 수 없습니다. ID: " + requestId
            ));

    validateCancelableStatus(request);

    LocalDateTime now = LocalDateTime.now();

    request.setRequestStatus("CANCELED");
    request.setUpdatedAt(now);

    approvalHistoryRepository
            .findFirstByRequestIdAndApprovalStatusInOrderByApprovalStepDesc(
                    requestId,
                    List.of("PENDING_APPROVAL", "PENDING", "WAITING", "REQUESTED")
            )
            .ifPresent(approvalHistory -> {
                approvalHistory.setApprovalStatus("CANCELED");
                approvalHistory.setCommentText("요청 취소");
                approvalHistory.setApprovedAt(now);
                approvalHistoryRepository.save(approvalHistory);
            });

    purchaseRequestRepository.save(request);

    return getPurchaseRequestDetail(requestId);
}

@Override
@Transactional
public void deletePurchaseRequest(Long requestId) {
    PurchaseRequest request = purchaseRequestRepository.findById(requestId)
            .filter(this::isActive)
            .orElseThrow(() -> new EntityNotFoundException(
                    "구매 요청을 찾을 수 없습니다. ID: " + requestId
            ));

    validateDeletableStatus(request);

    request.setDeletedYn("Y");
    request.setUpdatedAt(LocalDateTime.now());

    purchaseRequestRepository.save(request);
}

    private PurchaseRequestDto.ListResponse toListResponse(PurchaseRequest request) {
        // 이 헬퍼 메서드가 구동될 때도 안전하게 실시간 자식 품목들을 긁어와 묶어주도록 연동합니다.
        List<PurchaseRequestDto.ItemResponse> items = getItemResponses(request.getRequestId());
        long itemCount = (items != null) ? items.size() : 0L;

        return new PurchaseRequestDto.ListResponse(
                request.getRequestId(),
                nullToEmpty(request.getRequestNo()),
                nullToEmpty(request.getTitle()),
                getUserName(request.getRequestorId()),
                getDepartmentName(request.getRequestorId()),
                formatDate(request.getCreatedAt()),
                formatDate(request.getDueDate()),
                formatDate(request.getCreatedAt()),
                formatDate(request.getUpdatedAt()),
                itemCount, 
                request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO,
                resolvePriorityLabel(request),
                toRequestStatusLabel(request.getRequestStatus()),
                items 
        );
    }

    private List<PurchaseRequestDto.ItemResponse> getItemResponses(Long requestId) {
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
                .map(item -> toItemResponse(item, productMap.get(item.getProductId())))
                .toList();
    }

    private List<PurchaseRequestDto.AttachmentResponse>     getAttachmentResponses(Long requestId) {
        return attachmentRepository.findByRequestIdOrderByAttachmentIdAsc(requestId)
            .stream()
            .map(attachment -> new PurchaseRequestDto.AttachmentResponse(
                    attachment.getAttachmentId(),
                    attachment.getOriginalName(),
                    "/api/purchase-requests/attachments/"
                            + attachment.getAttachmentId()
                            + "/download"
            ))
            .toList();
    }

        private PurchaseRequestDto.ItemResponse toItemResponse(PurchaseRequestItem item, Product product) {
            int quantity = item.getRequestQuantity() != null ? item.getRequestQuantity() : 0;
        BigDecimal unitPrice = item.getEstimatedUnitPrice() != null
            ? item.getEstimatedUnitPrice()
            : BigDecimal.ZERO;
        BigDecimal estimatedAmount = calculateAmount(quantity, unitPrice);

            return new PurchaseRequestDto.ItemResponse(
                    item.getRequestItemId(),
                    item.getProductId(),
                    product != null ? nullToEmpty(product.getProductNo()) : "",
                    product != null ? nullToEmpty(product.getProductName()) : "",
                    product != null ? nullToEmpty(product.getCategoryName()) : "",
                    product != null ? nullToEmpty(product.getSpec()) : "",
                    quantity,
                    product != null ? nullToEmpty(product.getUnit()) : "",
                    unitPrice,
                    estimatedAmount,
                    FIXED_ITEM_REMARK,
                    product != null ? formatDateTime(product.getCreatedAt()) : "",
                    product != null ? formatDateTime(product.getUpdatedAt()) : ""
            );
        }

        private BigDecimal calculateAmount(int quantity, BigDecimal unitPrice) {
            BigDecimal safeUnitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
            return safeUnitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private long countByStatusLabel(List<PurchaseRequest> requests, String label) {
        return requests.stream()
                .filter(request -> label.equals(toRequestStatusLabel(request.getRequestStatus())))
                .count();
    }

    private boolean isActive(PurchaseRequest request) {
        return !"Y".equalsIgnoreCase(nullToEmpty(request.getDeletedYn()).trim());
    }

    private boolean contains(String value, String keyword) {
        return isBlank(keyword) || nullToEmpty(value).toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private boolean isSameDate(String value, String targetDate) {
    return isBlank(targetDate) || Objects.equals(value, targetDate);
    }

    private boolean isAllOrEquals(String filter, String allValue, String value) {
        return isBlank(filter) || allValue.equals(filter) || Objects.equals(filter, value);
    }

    private boolean isWithinRange(String value, String from, String to) {
        if (isBlank(value)) {
            return true;
        }
        if (!isBlank(from) && value.compareTo(from) < 0) {
            return false;
        }
        return isBlank(to) || value.compareTo(to) <= 0;
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

    private String getDepartmentName(Long userId) {
        if (userId == null) {
        return "-";
        }

        return userRepository.findById(userId)
            .map(Users::getDepartmentName)
            .filter(name -> !isBlank(name))
            .orElse("-");
    }

    private String resolvePriorityLabel(PurchaseRequest request) {
        if ("URGENT".equalsIgnoreCase(request.getPriority())) {
        return "긴급";
    }

        if ("NORMAL".equalsIgnoreCase(request.getPriority())) {
        return "일반";
    }

        if (request.getDueDate() != null &&
        !request.getDueDate().isAfter(LocalDate.now().plusDays(3))) {
        return "긴급";
    }

        return "일반";
}

    private String toRequestStatusLabel(String status) {
        if (status == null) {
            return "승인 대기";
        }
        return switch (status.trim().toUpperCase()) {
            case "DRAFT", "PENDING", "PENDING_APPROVAL", "WAITING", "REQUESTED" -> "승인 대기";
            case "APPROVED" -> "승인 완료";
            case "REJECTED" -> "반려";
            case "ORDERED" -> "발주 완료";
            case "CANCEL_REQUESTED", "CANCELED", "CANCELLED" -> "요청 취소";
            default -> status;
        };
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

    private String formatDateTime(LocalDateTime value) {
    return value == null ? "" : value.format(DATE_TIME_FORMATTER);
}

    private String formatDate(LocalDate value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private LocalDate parseRequiredDate(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + "은 필수입니다."
        );
    }

        return LocalDate.parse(value);
}

    private void validateCreateRequestDateIsToday(String requestDate) {
        LocalDate today = LocalDate.now();
        LocalDate parsedRequestDate = parseRequiredDate(requestDate, "요청일");

        if (!today.equals(parsedRequestDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청일은 오늘 날짜만 입력할 수 있습니다."
            );
        }
    }

    private void validateUpdateRequestDateMatchesCreatedAt(
            String requestDate,
            LocalDateTime createdAt
    ) {
        if (isBlank(requestDate)) {
            return;
        }

        LocalDate fixedRequestDate =
                createdAt != null ? createdAt.toLocalDate() : LocalDate.now();

        LocalDate parsedRequestDate = parseRequiredDate(requestDate, "요청일");

        if (!fixedRequestDate.equals(parsedRequestDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청일은 수정할 수 없습니다."
            );
        }
    }

    private Long resolveApproverId(Long preferredApproverId, Long requestorId) {
    if (preferredApproverId != null) {
        Users approver = userRepository.findById(preferredApproverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "선택한 승인 담당자를 찾을 수 없습니다."
                ));

        if (Objects.equals(preferredApproverId, requestorId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청자는 본인을 승인 담당자로 지정할 수 없습니다."
            );
        }

        if (!"Y".equalsIgnoreCase(approver.getUseYn())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사용 중지된 사용자는 승인 담당자로 지정할 수 없습니다."
            );
        }

        if (userRepository.countActiveRoleByUserId(preferredApproverId, "APPROVER") <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "선택한 사용자는 승인 담당자 권한이 없습니다."
            );
        }

        return preferredApproverId;
    }

    return userRepository.findFirstApproverId(requestorId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "승인 담당자를 찾을 수 없습니다."
            ));
}

    private String createRequestNumber(long requestId) {
        return "PR-" + LocalDate.now().getYear() + "-" + String.format("%04d", requestId);
    }

    private String normalizePriority(String priority, String urgency) {
    String value = priority;

    if (value == null || value.isBlank()) {
        value = urgency;
    }

    if (value == null || value.isBlank()) {
        return "NORMAL";
    }

    return switch (value.trim().toUpperCase()) {
        case "긴급", "URGENT", "HIGH" -> "URGENT";
        default -> "NORMAL";
    };
}

    private String normalizeRequestStatus(String status) {
        if (isBlank(status)) {
        return "PENDING_APPROVAL";
    }

    return switch (status.trim().toUpperCase()) {
        case "DRAFT", "PENDING", "PENDING_APPROVAL", "WAITING", "REQUESTED" -> "PENDING_APPROVAL";
        case "APPROVED" -> "APPROVED";
        case "REJECTED" -> "REJECTED";
        case "ORDERED" -> "ORDERED";
        case "CANCELED", "CANCELLED", "CANCEL_REQUESTED" -> "CANCELED";
        default -> "PENDING_APPROVAL";
    };
}

    private void validateEditableStatus(PurchaseRequest request) {
    String status = normalizeRequestStatus(request.getRequestStatus());

    if (!"PENDING_APPROVAL".equals(status)) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "승인 대기 상태의 구매 요청만 수정할 수 있습니다. 현재 상태: "
                        + toRequestStatusLabel(status)
        );
    }
}

private void validateCancelableStatus(PurchaseRequest request) {
    String status = normalizeRequestStatus(request.getRequestStatus());

    if (!"PENDING_APPROVAL".equals(status)) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "승인 대기 상태의 구매 요청만 취소할 수 있습니다. 현재 상태: "
                        + toRequestStatusLabel(status)
        );
    }
}

private void validateDeletableStatus(PurchaseRequest request) {
    String status = normalizeRequestStatus(request.getRequestStatus());

    if (!"PENDING_APPROVAL".equals(status)) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "승인 대기 상태의 구매 요청만 삭제할 수 있습니다. 현재 상태: "
                        + toRequestStatusLabel(status)
        );
    }
}

    private BigDecimal toBigDecimal(Long value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseRequestDto.ListResponse> getApprovedRequestsWithoutPaging() {
        // 1. APPROVED 상태인 마스터 구매요청 목록을 가져옵니다.
        List<PurchaseRequest> requests = purchaseRequestRepository.findByRequestStatus("APPROVED");
        
        // 2. 스트림을 돌며 마스터 정보와 찐 자식 품목 객체 리스트까지 완벽하게 밀봉 바인딩합니다.
        return requests.stream()
                .map(request -> {
                    // 🚀 [찐 자식 품목 수집 엔진 작동]: 팀원의 족보 메서드를 호출해 실제 DB 품목 리스트를 가져옵니다.
                    List<PurchaseRequestDto.ItemResponse> actualItems = getItemResponses(request.getRequestId());
                    long actualItemCount = (actualItems != null) ? actualItems.size() : 0L;

                    // 🚀 [100% 무결점 매핑]: 하드코딩 일절 없이 팀원의 정석 인프라 헬퍼들을 14개 인자 규격에 딱 맞춰 배달합니다!
                    return new PurchaseRequestDto.ListResponse(
                            request.getRequestId(),
                            nullToEmpty(request.getRequestNo()),
                            nullToEmpty(request.getTitle()),
                            getUserName(request.getRequestorId()),       // 4. 찐 작성자명 동적 연동
                            getDepartmentName(request.getRequestorId()), // 5. 찐 부서명 동적 연동
                            formatDate(request.getCreatedAt()),
                            formatDate(request.getDueDate()),            // 7. 찐 입고희망일 연동
                            formatDate(request.getCreatedAt()),
                            formatDate(request.getUpdatedAt()),
                            actualItemCount,                             // 10. 찐 품목 개수 카운트
                            request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO,
                            resolvePriorityLabel(request),
                            toRequestStatusLabel(request.getRequestStatus()),
                            actualItems 
                    );
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<PurchaseRequestDto.ListResponse>    getAllPurchaseRequestRowsForExcel() {
        return purchaseRequestRepository.findActiveRequestsOrderByRequestIdDesc()
                .stream()
                .map(this::toListResponse)
             .toList();
        }

    }

