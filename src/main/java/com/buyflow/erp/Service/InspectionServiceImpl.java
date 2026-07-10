package com.buyflow.erp.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buyflow.erp.Dto.InspectionDto;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Entity.Inspection;
import com.buyflow.erp.Entity.PurchaseOrder;
import com.buyflow.erp.Entity.Receipt;
import com.buyflow.erp.Entity.ReceiptItem;
import com.buyflow.erp.Entity.Stock;
import com.buyflow.erp.Entity.StockHistory;
import com.buyflow.erp.Entity.Supplier;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Entity.Warehouse;
import com.buyflow.erp.Entity.Product;
import com.buyflow.erp.Repository.InspectionRepository;
import com.buyflow.erp.Repository.PurchaseOrderRepository;
import com.buyflow.erp.Repository.ReceiptItemRepository;
import com.buyflow.erp.Repository.ReceiptRepository;
import com.buyflow.erp.Repository.StockHistoryRepository;
import com.buyflow.erp.Repository.StockRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Repository.WarehouseRepository;
import com.buyflow.erp.Repository.ProductRepository;
import com.buyflow.erp.Repository.SupplierRepository;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InspectionServiceImpl implements InspectionService {
	private final InspectionRepository inspectionRepository;
	private final ReceiptItemRepository receiptItemRepository;
	private final ReceiptRepository receiptRepository;

	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;
	private final UserRepository userRepository;
	private final PurchaseOrderRepository purchaseOrderRepository;
	private final WarehouseRepository warehouseRepository;
	private final ProductRepository productRepository;
	private final SupplierRepository supplierRepository;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<InspectionDto.ListResponse> getInspections(InspectionDto.SearchCondition condition) {
	    int displayPage = (condition.getPage() != null) ? condition.getPage() : 1;
	    int safeSize = (condition.getSize() != null && condition.getSize() > 0) ? condition.getSize() : 15;
	    
	    int safePage = Math.max(displayPage - 1, 0);
	    Pageable pageable = PageRequest.of(safePage, safeSize);

	    String result = (condition.getInspectionResult() == null || condition.getInspectionResult().isEmpty()
	            || condition.getInspectionResult().equals("전체")) ? null : condition.getInspectionResult();

	    Page<Inspection> inspectionPage = inspectionRepository.searchInspections(condition.getReceiptItemId(), result, pageable);

	    List<InspectionDto.ListResponse> dtoList = inspectionPage.getContent().stream()
	            .map(inspection -> new InspectionDto.ListResponse(
	                    inspection.getInspectionId(),
	                    inspection.getInspectionDate(), 
	                    inspection.getInspectionType(),
	                    inspection.getInspectionResult(), 
	                    inspection.getReceiptItemId(),
	                    inspection.getUser() != null ? inspection.getUser().getUserId() : null,
	                    inspection.getQuantity(), 
	                    inspection.getDefectQuantity()))
	            .collect(Collectors.toList());

	    return new PageResponse<>(dtoList, new PageResponse.Pagination(
	            inspectionPage.getNumber() + 1,
	            inspectionPage.getSize(), 
	            inspectionPage.getTotalElements(), 
	            inspectionPage.getTotalPages()));
	}
	
	@Override
	@Transactional(readOnly = true)
	public PageResponse<InspectionDto.Response> getPendingInspections(
        InspectionDto.SearchCondition condition) {

	    int displayPage = condition.getPage() != null ? condition.getPage() : 1;
	    int safePage = Math.max(displayPage - 1, 0);
	    int safeSize = condition.getSize() != null && condition.getSize() > 0
	            ? condition.getSize()
	            : 15;
	
	    Pageable pageable = PageRequest.of(safePage, safeSize);
	
	    String inspectionNumber = blankToNull(condition.getInspectionNumber());
	    String receiptNumber = blankToNull(condition.getReceiptNumber());
	    String orderNumber = blankToNull(condition.getOrderNumber());
	    String supplierName = optionToNull(condition.getSupplierName(), "전체 공급업체");
	    String warehouseName = optionToNull(condition.getWarehouseName(), "전체 창고");
	    String priority = optionToNull(condition.getPriority(), "전체");
	    String receivedFrom = blankToNull(condition.getReceivedFrom());
	    String receivedTo = blankToNull(condition.getReceivedTo());
	
	    String summaryFilter = blankToNull(condition.getSummaryFilter());
	
	    if (summaryFilter == null) {
	    	summaryFilter = "ALL";
	    }
	
	    Page<Receipt> receiptPage = receiptRepository.searchPendingReceipts(
	        inspectionNumber,
	        receiptNumber,
	        orderNumber,
	        supplierName,
	        warehouseName,
	        priority,
	        receivedFrom,
	        receivedTo,
	        summaryFilter,
	        pageable);
	
	    List<InspectionDto.Response> dtoList = receiptPage.getContent()
	            .stream()
	            .map(receipt -> buildInspectionResponse(receipt, false))
	            .toList();
	
	    return new PageResponse<>(
	            dtoList,
	            new PageResponse.Pagination(
	                    receiptPage.getNumber() + 1,
	                    receiptPage.getSize(),
	                    receiptPage.getTotalElements(),
	                    receiptPage.getTotalPages()));
	}
	
    @Override
    @Transactional(readOnly = true)
    public PageResponse<InspectionDto.Response> getCompletedInspections(
    InspectionDto.SearchCondition condition) {

	    int displayPage = condition.getPage() != null ? condition.getPage() : 1;
	    int safePage = Math.max(displayPage - 1, 0);
	    int safeSize = condition.getSize() != null && condition.getSize() > 0
		    ? condition.getSize()
		    : 15;

		Pageable pageable = PageRequest.of(safePage, safeSize);
		
		String inspectionNumber = blankToNull(condition.getInspectionNumber());
		String receiptNumber = blankToNull(condition.getReceiptNumber());
		String orderNumber = blankToNull(condition.getOrderNumber());
		String supplierName = optionToNull(condition.getSupplierName(), "전체 공급업체");
		String warehouseName = optionToNull(condition.getWarehouseName(), "전체 창고");
		String receivedFrom = blankToNull(condition.getReceivedFrom());
		String receivedTo = blankToNull(condition.getReceivedTo());
		
		String inspectionResult = condition.getInspectionResult();

	    if (inspectionResult == null
	    || inspectionResult.isBlank()
	    || inspectionResult.equals("전체")
	    || inspectionResult.equals("ALL")) {
	    	inspectionResult = null;
	    }

	    Page<Receipt> receiptPage = receiptRepository.searchCompletedReceipts(
	        inspectionNumber,
	        receiptNumber,
	        orderNumber,
	        supplierName,
	        warehouseName,
	        receivedFrom,
	        receivedTo,
	        inspectionResult,
	        pageable);

	    List<InspectionDto.Response> dtoList = receiptPage.getContent()
	        .stream()
	        .map(receipt -> buildInspectionResponse(receipt, true))
	        .toList();
	
	    return new PageResponse<>(
	        dtoList,
	        new PageResponse.Pagination(
	                receiptPage.getNumber() + 1,
	                receiptPage.getSize(),
	                receiptPage.getTotalElements(),
	                receiptPage.getTotalPages()));
    }

	@Override
	@Transactional(readOnly = true)
	public InspectionDto.Response getPendingInspectionDetail(Long receiptId) {
    	Receipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new RuntimeException("입고 정보를 찾을 수 없습니다. ID: " + receiptId));

    	return buildInspectionResponse(receipt, true);
	}

	private InspectionDto.Response buildInspectionResponse(
        Receipt receipt,
        boolean includeItems) {
	    List<ReceiptItem> receiptItems = receiptItemRepository.findByReceiptId(receipt.getReceiptId());
	
	    PurchaseOrder order = receipt.getOrderId() != null
	            ? purchaseOrderRepository.findById(receipt.getOrderId()).orElse(null)
	            : null;
	
	    Supplier supplier = order != null ? order.getSupplier() : null;
	
	    Warehouse warehouse = receipt.getWarehouseCode() != null
	            ? warehouseRepository.findById(receipt.getWarehouseCode()).orElse(null)
	            : null;
	
	    List<InspectionDto.InspectionItemDto> items = includeItems
	            ? receiptItems.stream()
	                    .map(this::buildInspectionItemDto)
	                    .toList()
	            : List.of();
	
	    long totalReceivedQuantity = receiptItems.stream()
	            .mapToLong(item -> item.getReceiptQty() == null ? 0L : item.getReceiptQty())
	            .sum();
	
	    String receivedAt = receipt.getReceiptDate() != null
	            ? receipt.getReceiptDate().toLocalDate().toString()
	            : "";
	
	    String dueAt = getInspectionDueAtText(receipt);
	
	    String status = resolveReceiptInspectionStatus(receiptItems);
	
	    InspectionDto.InspectionResultDto result = null;
	
	    if (!"PENDING".equals(status) && includeItems) {
	        result = InspectionDto.InspectionResultDto.builder()
	            .status(status)
	            .inspectorName(findInspectorName(receiptItems))
	            .inspectedAt(findLastInspectedAt(receiptItems))
	            .note(findResultNote(receiptItems))
	            .items(items)
	            .build();
	    }

    return InspectionDto.Response.builder()
            .id(receipt.getReceiptId())
            .inspectionNumber("IQC-2026-" + String.format("%04d", receipt.getReceiptId()))
            .receiptNumber(receipt.getReceiptNo())
            .orderNumber(order != null
                    ? "PO-2026-" + String.format("%04d", order.getOrderId())
                    : "-")
            .supplierName(supplier != null ? supplier.getSupplierName() : "-")
            .warehouseName(warehouse != null ? warehouse.getWarehouseName() : "-")
            .receivedAt(receivedAt)
            .inspectionDueAt(dueAt)
            .priority(resolveInspectionPriority(receipt))
            .status(status)
            .receivedBy(receipt.getLoginId())
            .itemCount(receiptItems.size())
            .totalReceivedQuantity(totalReceivedQuantity)
            .items(items)
            .inspectionResult(result)
            .build();
	}

    private String getInspectionDueAtText(Receipt receipt) {
    	LocalDate dueDate = getInspectionDueDate(receipt);

    	return dueDate == null ? "" : dueDate.toString();
    }

	private LocalDate getInspectionDueDate(Receipt receipt) {
	    if (receipt == null || receipt.getReceiptDate() == null) {
	        return null;
	    }
	
	    return receipt.getReceiptDate().toLocalDate().plusDays(1);
	}

	private String resolveInspectionPriority(Receipt receipt) {
	    LocalDate dueDate = getInspectionDueDate(receipt);
	
	    if (dueDate == null) {
	        return "일반";
	    }
	
	    return !dueDate.isAfter(LocalDate.now()) ? "긴급" : "일반";
	}

    private InspectionDto.InspectionItemDto buildInspectionItemDto(ReceiptItem receiptItem) {
            Product product = receiptItem.getProductId() != null
            ? productRepository.findById(receiptItem.getProductId()).orElse(null)
            : null;

            Long receivedQty = receiptItem.getReceiptQty() == null ? 0L : receiptItem.getReceiptQty();
            Long acceptedQty = receiptItem.getAcceptedQty() == null ? receivedQty : receiptItem.getAcceptedQty();
            Long defectiveQty = receiptItem.getDefectQty() == null ? 0L : receiptItem.getDefectQty();

		    Inspection inspection = inspectionRepository
		        .findByReceiptItemId(receiptItem.getReceiptItemId())
		        .orElse(null);

	    return InspectionDto.InspectionItemDto.builder()
	        .id(receiptItem.getReceiptItemId())
	        .receiptItemId(receiptItem.getReceiptItemId())
	
	        .itemCode(product != null ? product.getProductNo() : "-")
	        .itemName(product != null ? product.getProductName() : "-")
	        .category(product != null ? product.getCategoryName() : "-")
	        .specification(product != null ? product.getSpec() : "-")
	        .unit(product != null ? product.getUnit() : "-")
	
	        .lotNumber("-")
	        .receivedQuantity(receivedQty)
	        .acceptedQuantity(acceptedQty)
	        .defectiveQuantity(defectiveQty)
	
	        // 검수 완료된 품목이면 INSPECTION 테이블의 저장값 우선 표시
	        .defectReason(inspection != null ? inspection.getNotes() : null)
	        .disposition(
	                inspection != null
	                        && inspection.getDisposition() != null
	                        && !inspection.getDisposition().isBlank()
	                                ? inspection.getDisposition()
	                                : "NONE").build();
    }

    private String resolveReceiptInspectionStatus(List<ReceiptItem> receiptItems) {
      if (receiptItems == null || receiptItems.isEmpty()) {
            return "PENDING";
    }

      boolean hasPendingItem = receiptItems.stream()
        .anyMatch(item -> !inspectionRepository.existsByReceiptItemId(item.getReceiptItemId()));

      if (hasPendingItem) {return "PENDING";}

      boolean hasDefect = receiptItems.stream()
            .anyMatch(item -> item.getDefectQty() != null && item.getDefectQty() > 0);
      
      return hasDefect ? "DEFECT" : "PASS";
    }

	private String findInspectorName(List<ReceiptItem> receiptItems) {
	    if (receiptItems == null || receiptItems.isEmpty()) {
	        return "-";
	    }
	
	    for (ReceiptItem item : receiptItems) {
	        Inspection inspection = inspectionRepository.findByReceiptItemId(item.getReceiptItemId()).orElse(null);
	
	        if (inspection != null && inspection.getUser() != null) {
	            return inspection.getUser().getUserName();
	        }
	    }
	
	    return "-";
	}

	private LocalDateTime findLastInspectedAt(List<ReceiptItem> receiptItems) {
	    if (receiptItems == null || receiptItems.isEmpty()) {
	        return null;
	    }
	
	    return receiptItems.stream()
            .map(item -> inspectionRepository.findByReceiptItemId(item.getReceiptItemId()).orElse(null))
            .filter(Objects::nonNull)
            .map(Inspection::getCreatedAt)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(null);
	}

	private String findResultNote(List<ReceiptItem> receiptItems) {
	    if (receiptItems == null || receiptItems.isEmpty()) {
	        return null;
	    }
	
	    return receiptItems.stream()
            .map(item -> inspectionRepository.findByReceiptItemId(item.getReceiptItemId()).orElse(null))
            .filter(Objects::nonNull)
            .map(Inspection::getNotes)
            .filter(note -> note != null && !note.isBlank())
            .findFirst()
            .orElse(null);
	}

	private String blankToNull(String value) {
	    if (value == null || value.isBlank()) {
	        return null;
	    }
	
	    return value.trim();
	}
	
	private String optionToNull(String value, String allOptionText) {
	    if (value == null || value.isBlank() || value.equals(allOptionText)) {
	        return null;
	    }
	
	    return value.trim();
	}
	
	@Override
	@Transactional(readOnly = true)
	public Inspection getInspection(Long inspectionId) {
		return inspectionRepository.findById(inspectionId)
				.orElseThrow(() -> new EntityNotFoundException("해당 검수 내역을 찾을 수 없습니다. ID: " + inspectionId));
	}

	@Override
	@Transactional
	public void saveInspection(InspectionDto.CreateRequest request) {
		
		if (request.getReceiptItemId() == null) {
			throw new RuntimeException("입고 품목 ID가 없습니다.");
		}
		
		// 중복 검수 예외 처리
		if (inspectionRepository.existsByReceiptItemId(request.getReceiptItemId())) {
			throw new RuntimeException("이미 검수 완료된 입고입니다.");
		}
		
		ReceiptItem receiptItem = receiptItemRepository.findById(request.getReceiptItemId())
				.orElseThrow(() -> new RuntimeException("입고 상세 내역을 찾을 수 없습니다."));
		
		Receipt receipt = receiptRepository.findById(receiptItem.getReceiptId())
				.orElseThrow(() -> new RuntimeException("입고 마스터 정보를 찾을 수 없습니다."));

		Users user = userRepository.findById(request.getInspectorId())
				.orElseThrow(() -> new RuntimeException("존재하지 않는 검수자입니다."));
		
		Long receiptQty = receiptItem.getReceiptQty() == null ? 0L : receiptItem.getReceiptQty();
		Long oldAcceptedQty = receiptItem.getAcceptedQty() == null ? 0L : receiptItem.getAcceptedQty();
		
		Long finalDefectQty = request.getDefectQuantity() != null 
				? request.getDefectQuantity()
				: (request.getDefectQty() != null ? request.getDefectQty() : 0L);

		Long finalAcceptedQty = request.getAcceptedQuantity();
		
		if (finalAcceptedQty == null) {
			finalAcceptedQty = receiptQty - finalDefectQty;
		}
		
		if (finalDefectQty < 0 || finalAcceptedQty < 0) {
			throw new RuntimeException("합격 수량과 불량 수량은 0 이상이어야 합니다.");
		}
		
		if(!receiptQty.equals(finalAcceptedQty + finalDefectQty)) {
			throw new RuntimeException("합격 수량과 불량 수량의 합이 입고 수량과 일치하지 않습니다.");
		}
		
		// 검수 정보 빌드 및 저장
		Inspection inspection = new Inspection();
		inspection.setReceiptItemId(request.getReceiptItemId());
		inspection.setUser(user);
		inspection.setInspectionDate(request.getInspectionDate());
		inspection.setInspectionType(request.getInspectionType());
		inspection.setQuantity(request.getQuantity() != null ? request.getQuantity() : receiptQty);
        inspection.setDefectQuantity(finalDefectQty);
        inspection.setInspectionResult(request.getInspectionResult());
        inspection.setNotes(request.getNotes());
        inspection.setDisposition(
        request.getDisposition() == null || request.getDisposition().isBlank()
                ? "NONE"
                : request.getDisposition());
        inspection.setCreatedAt(LocalDateTime.now());

		inspectionRepository.save(inspection);

		String warehouseCode = receipt.getWarehouseCode();

		// 재고 조회
		Stock stock = stockRepository.findByProductIdAndWarehouseCode(receiptItem.getProductId(), warehouseCode)
				.orElseThrow(() -> new RuntimeException("해당 품목의 창고 재고가 존재하지 않습니다."));

		Long beforeQty = stock.getQuantity() == null ? 0L : stock.getQuantity().longValue();
		Long correctionQty = finalAcceptedQty - oldAcceptedQty;

		if (correctionQty != 0) {
			Long afterQty = beforeQty + correctionQty;
			
			if (afterQty < 0) {
				throw new RuntimeException("검수 보정 후 재고가 음수가 될 수 없습니다.");
			}

			stock.setQuantity(afterQty.intValue());
			stock.setUpdatedAt(LocalDateTime.now());
			stockRepository.save(stock);
			
			// 재고이력 생성 및 저장
			StockHistory history = new StockHistory();
			history.setStockId(stock.getStockId());
			history.setHistoryType("INSPECTION_ADJUST");
			history.setChangeQty(correctionQty);
			history.setBeforeQty(beforeQty);
			history.setAfterQty(afterQty);
			history.setRelatedReceiptItemId(receiptItem.getReceiptItemId());
			history.setRelatedOrderItemId(receiptItem.getOrderItemId());
			history.setReason("검수 결과에 따른 재고 보정");
			history.setCreatedAt(LocalDateTime.now());
			history.setCreatedBy(user.getUserName());

			stockHistoryRepository.save(history);
		}
		
			receiptItem.setDefectQty(finalDefectQty);
			receiptItem.setAcceptedQty(finalAcceptedQty);
		            receiptItem.setReceiptItemStatus("INSPECTED");
			receiptItemRepository.save(receiptItem);
	}
	
	@Override
    @Transactional
    public void saveInspectionResult(Long receiptId, InspectionDto.ResultRequest request) {
		if (request.getItems() == null || request.getItems().isEmpty()) {
			throw new RuntimeException("검수할 품목이 없습니다.");
		}

    for (InspectionDto.ResultItemRequest itemRequest : request.getItems()) {
            Long receiptItemId = itemRequest.getReceiptItemId() != null
                    ? itemRequest.getReceiptItemId()
                    : itemRequest.getId();

	    InspectionDto.CreateRequest createRequest = new InspectionDto.CreateRequest();
	
	    createRequest.setReceiptItemId(receiptItemId);
	    createRequest.setInspectorId(request.getInspectorId());
	    createRequest.setInspectionDate(
	            request.getInspectedAt() == null
	                    ? java.time.LocalDate.now()
	                    : request.getInspectedAt().toLocalDate());
	    createRequest.setInspectionType("RECEIPT");
	    createRequest.setQuantity(itemRequest.getReceivedQuantity());
	    createRequest.setAcceptedQuantity(itemRequest.getAcceptedQuantity());
	    createRequest.setDefectQuantity(itemRequest.getDefectiveQuantity());
	    createRequest.setInspectionResult(
	            itemRequest.getDefectiveQuantity() != null && itemRequest.getDefectiveQuantity() > 0
	                    ? "DEFECT"
	                    : "PASS");
	    createRequest.setNotes(
	            itemRequest.getDefectReason() != null && !itemRequest.getDefectReason().isBlank()
	                    ? itemRequest.getDefectReason()
	                    : request.getNote());
	    createRequest.setDisposition(
	            itemRequest.getDisposition() == null || itemRequest.getDisposition().isBlank()
	                    ? "NONE"
	                    : itemRequest.getDisposition());
    	saveInspection(createRequest);
	}

    	updateReceiptStatusAfterInspection(receiptId);
	}

	private void updateReceiptStatusAfterInspection(Long receiptId) {
	    List<ReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);
	
	    if (items == null || items.isEmpty()) {return;}
	
	    boolean allInspected = items.stream()
	            .allMatch(item -> inspectionRepository.existsByReceiptItemId(item.getReceiptItemId()));
	
	    if (!allInspected) {return;}
	
	    boolean hasDefect = items.stream()
	            .anyMatch(item -> item.getDefectQty() != null && item.getDefectQty() > 0);
	
	    Receipt receipt = receiptRepository.findById(receiptId)
	            .orElseThrow(() -> new RuntimeException("입고 정보를 찾을 수 없습니다."));
	
	    receipt.setReceiptStatus(hasDefect ? "INSPECTED_DEFECT" : "INSPECTED_PASS");
	    receipt.setUpdatedAt(LocalDateTime.now());
	
	    receiptRepository.save(receipt);
	}
	
    @Override
    @Transactional(readOnly = true)
    public InspectionDto.PendingSummaryResponse getInspectionSummary() {
	    long total = receiptRepository.countPendingReceipts();
	    long receivedToday = receiptRepository.countPendingReceivedTodayReceipts();
	    long urgent = receiptRepository.countPendingUrgentReceipts();
	    long overdue = receiptRepository.countPendingOverdueReceipts();

	    return new InspectionDto.PendingSummaryResponse(
	        total,
	        receivedToday,
	        urgent,
	        overdue);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionDto.SummaryResponse getCompletedInspectionSummary() {
            long totalCount = receiptRepository.countCompletedReceipts();
            long passCount = receiptRepository.countCompletedPassReceipts();
            long defectCount = receiptRepository.countCompletedDefectReceipts();

            return new InspectionDto.SummaryResponse(totalCount, 0, passCount, defectCount);
    }

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> getInspectionFilterOptions() {
    	List<String> suppliers = new ArrayList<>();
    	suppliers.add("전체 공급업체");

    	suppliers.addAll(
            supplierRepository.findAll()
                    .stream()
                    .map(Supplier::getSupplierName)
                    .filter(Objects::nonNull)
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .sorted()
                    .toList());

	    List<String> warehouses = new ArrayList<>();
	    warehouses.add("전체 창고");
	
	    warehouses.addAll(
	            warehouseRepository.findAll()
	                    .stream()
	                    .map(Warehouse::getWarehouseName)
	                    .filter(Objects::nonNull)
	                    .filter(name -> !name.isBlank())
	                    .distinct()
	                    .sorted()
	                    .toList());
    
	    return Map.of(
	            "suppliers", suppliers,
	            "warehouses", warehouses,
	            "priorities", List.of("전체", "일반", "긴급"),
	            
	            "inspectionTypes", List.of("입고검수", "품질검수", "출하검수"),
	            "inspectionResults", List.of("합격", "불합격", "부분합격", "검수대기"),
	            "dispositions", List.of("입고", "반품", "폐기", "재검수"));
	}
}
        

