package com.buyflow.erp.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.PurchaseOrderDto;
import com.buyflow.erp.Entity.Product;
import com.buyflow.erp.Entity.PurchaseOrder;
import com.buyflow.erp.Entity.PurchaseOrderItem;
import com.buyflow.erp.Entity.PurchaseRequest;
import com.buyflow.erp.Entity.PurchaseRequestItem;
import com.buyflow.erp.Entity.Supplier;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Entity.Warehouse;
import com.buyflow.erp.Repository.AttachmentRepository;
import com.buyflow.erp.Repository.ProductRepository;
import com.buyflow.erp.Repository.PurchaseOrderItemRepository;
import com.buyflow.erp.Repository.PurchaseOrderRepository;
import com.buyflow.erp.Repository.PurchaseRequestItemRepository;
import com.buyflow.erp.Repository.PurchaseRequestRepository;
import com.buyflow.erp.Repository.SupplierRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Repository.WarehouseRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

	private static final Logger log = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository orderItemRepository; 
    private final ProductRepository productRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final AttachmentRepository attachmentRepository;
    private final WarehouseRepository warehouseRepository;

    @Override
    @Transactional
    public PurchaseOrderDto.Response createOrder(PurchaseOrderDto.Request request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 데이터가 비어있습니다.");
        }
        
        Warehouse warehouse = null;
        if (request.getWarehouseCode() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseCode())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다. ID: " + request.getWarehouseCode()));
        } else {
            throw new IllegalArgumentException("입고 창고 코드는 필수입니다.");
        }
        
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new EntityNotFoundException("공급업체가 존재하지 않습니다. ID: " + request.getSupplierId()));
        
        Long userIdToFind = request.getCreatedBy();
        if (userIdToFind == null || userIdToFind <= 0) {
            throw new IllegalArgumentException("발주 담당자 정보가 없습니다. 로그인 사용자 정보를 확인하세요.");
        }
        
        final Long finalUserId = userIdToFind;
        
        Users user = userRepository.findById(userIdToFind)
                .orElseThrow(() -> new EntityNotFoundException("사용자가 존재하지 않습니다. ID: " + finalUserId));
        
        PurchaseRequest purchaseRequest = null;
        if (request.getRequestId() != null) {
            purchaseRequest = purchaseRequestRepository.findById(request.getRequestId())
                    .orElseThrow(() -> new EntityNotFoundException("구매 요청을 찾을 수 없습니다. ID: " + request.getRequestId()));
        }
        
        LocalDateTime finalDueDate = request.getDueDate();

        if (finalDueDate == null && request.getExpectedReceiptTo() != null) {
            finalDueDate = request.getExpectedReceiptTo().atStartOfDay()
            				.plusHours(23)
            				.plusMinutes(59)
            				.plusSeconds(59); 
        }

        if (finalDueDate == null) {
            finalDueDate = LocalDateTime.now().plusDays(7);
        }
        
        LocalDate finalExpectedFrom = request.getExpectedReceiptFrom();
        
        String finalOrderNo = request.getOrderNo();
        if (finalOrderNo == null || finalOrderNo.trim().isEmpty()) {
            String todayPrefix = "PO-" + java.time.LocalDate.now().toString() + "-";
            String maxOrderNo = orderRepository.findMaxOrderNoByToday(todayPrefix + "%"); 

            if (maxOrderNo == null || maxOrderNo.isEmpty()) {
                finalOrderNo = todayPrefix + "0001";
            } else {
                try {
                    String lastPart = maxOrderNo.substring(maxOrderNo.lastIndexOf("-") + 1);
                    String numericPart = lastPart.replaceAll("[^0-9]", "");
                    int nextSeq = numericPart.isEmpty() ? 1 : Integer.parseInt(numericPart) + 1;
                    finalOrderNo = String.format("PO-%s-%04d", java.time.LocalDate.now().toString(), nextSeq);
                } catch (Exception e) {
                    finalOrderNo = todayPrefix + "0001";
                }
            }
        }

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNo(finalOrderNo.trim())
                .supplier(supplier)
                .user(user)
                .purchaseRequest(purchaseRequest)
                .warehouse(warehouse)
                .createdAt(LocalDateTime.now()) 
                .orderStatus(request.getOrderStatus() != null ? request.getOrderStatus() : "CONFIRMED")
                .dueDate(finalDueDate)
                .expectedReceiptFrom(finalExpectedFrom)
                .totalAmount(0.0) 
                .memo(request.getMemo())
                .attachment(request.getAttachmentId() != null ? attachmentRepository.findById(request.getAttachmentId()).orElse(null) : null)
                .build();
        
        long totalSupplyAmount = 0L;
        long totalVatAmount = 0L;
        
        for (PurchaseOrderDto.Item itemReq : request.getItems()) {
        	Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. ID: " + itemReq.getProductId()));
            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())   
                    .unitPrice(itemReq.getUnitPrice()) 
                    .build();
            
            order.addItem(item); 
            
            long lineSupply = (long) (itemReq.getUnitPrice() * itemReq.getQuantity());
            long lineVat = (long) Math.floor(lineSupply * 0.1);
            
            totalSupplyAmount += lineSupply;
            totalVatAmount += lineVat;
        } 

        double finalTotalAmount = (double) (totalSupplyAmount + totalVatAmount);
        order.setTotalAmount(finalTotalAmount);
        
        PurchaseOrder savedOrder = orderRepository.save(order);
        
        PurchaseOrder refreshedOrder = orderRepository.findByIdWithItems(savedOrder.getOrderId())
                .orElse(savedOrder);
        
        return PurchaseOrderDto.Response.from(refreshedOrder);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderDto.Response getOrderWithItems(Long orderId) {
    	// Fetch Join
        PurchaseOrder order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("발주를 찾을 수 없습니다. ID: " + orderId));
        
        // 혹시 몰라 지연 로딩 방지
        if (order.getItems() != null) {
            Hibernate.initialize(order.getItems());
        }
        
        // 초기화
        if (order.getPurchaseRequest() != null) {
            Hibernate.initialize(order.getPurchaseRequest());
        }

        return PurchaseOrderDto.Response.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderDto.ItemResponse> getApprovedRequestItems(Long requestId) {        
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
                    
                    Long defaultPrice = 0L;
                    if (item.getEstimatedUnitPrice() != null && item.getEstimatedUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                        defaultPrice = item.getEstimatedUnitPrice().longValue();
                    } else if (product != null && product.getUnitPrice() != null) {
                        defaultPrice = product.getUnitPrice();
                    }
                    
                    return PurchaseOrderDto.ItemResponse.builder()
                            .requestItemId(item.getRequestItemId()) 
                            .itemCode(product != null ? product.getProductNo() : "") 
                            .itemName(product != null ? product.getProductName() : "") 
                            .specification(product != null ? product.getSpec() : "") 
                            .requestedQuantity(item.getRequestQuantity()) 
                            .orderQuantity(item.getRequestQuantity())     
                            .unit(product != null ? product.getUnit() : "") 
                            .unitPrice(defaultPrice) 
                            .build();
	                })
	                		.toList();
    }
 
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderDto.Response> getOrderList(PurchaseOrderDto.SearchCondition condition) {
        int safePage = Math.max(condition.getPage(), 0);
        int safeSize = Math.max(condition.getSize(), 1);
        
        Pageable pageable = PageRequest.of(safePage, safeSize);
        
        String orderNo = (condition.getOrderNo() == null || condition.getOrderNo().isEmpty()) ? null : condition.getOrderNo();
        String requestNo = (condition.getRequestNo() == null || condition.getRequestNo().isEmpty()) ? null : condition.getRequestNo();
        String supplierName = (condition.getSupplierName() == null || condition.getSupplierName().isEmpty() || condition.getSupplierName().equals("전체 공급업체")) ? null : condition.getSupplierName();
        String userName = (condition.getUserName() == null || condition.getUserName().isEmpty()) ? null : condition.getUserName();
        String status = (condition.getOrderStatus() == null || condition.getOrderStatus().isEmpty() || condition.getOrderStatus().equals("전체")) ? null : condition.getOrderStatus();

        Page<PurchaseOrder> orderPage = orderRepository.searchOrdersAdvanced(
                orderNo,
                requestNo,
                supplierName,
                userName,
                status,
                pageable
        );
        
        if (orderPage != null && orderPage.getContent() != null) {
            for (PurchaseOrder po : orderPage.getContent()) {
                log.info(" 발주번호: {} | TotalAmount: {} | CreatedAt: {} | 품목개수: {}",
                    po.getOrderNo(),
                    po.getTotalAmount(),
                    po.getCreatedAt(),
                    po.getItems() != null ? po.getItems().size() : 0
                );
            }
        }

        List<PurchaseOrderDto.Response> dtoList = orderPage.getContent().stream()
                        .map(PurchaseOrderDto.Response::from)
                        .toList();
        
        return new PageResponse<>(
                dtoList,
                new PageResponse.Pagination(
                        orderPage.getNumber() + 1,
                        orderPage.getSize(),
                        orderPage.getTotalElements(),
                        orderPage.getTotalPages()
                )
        );
    }
    
    @Override
    @Transactional
    public PurchaseOrderDto.Response updateOrder(Long orderId, PurchaseOrderDto.Request request) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("발주를 찾을 수 없습니다. ID: " + orderId));

        if ("ORDERED".equals(order.getOrderStatus()) || "CANCELLED".equals(order.getOrderStatus())) {
            throw new RuntimeException("이미 승인 완료되거나 취소된 발주는 수정할 수 없습니다.");
        }

        // 기본 정보 업데이트
        if (request.getRequestId() != null) {
            PurchaseRequest purchaseRequest = purchaseRequestRepository.findById(request.getRequestId())
                    .orElseThrow(() -> new EntityNotFoundException("구매 요청을 찾을 수 없습니다. ID: " + request.getRequestId()));
            order.setPurchaseRequest(purchaseRequest);
        }
        
        if (request.getOrderStatus() != null) {
            order.setOrderStatus(request.getOrderStatus());
        }
        
        if (request.getDueDate() != null) {
            order.setDueDate(request.getDueDate());
        }
        
        if (request.getExpectedReceiptFrom() != null) {
            order.setExpectedReceiptFrom(request.getExpectedReceiptFrom());
        }
        
        if (request.getExpectedReceiptTo() != null) {
            order.setDueDate(request.getExpectedReceiptTo().atStartOfDay()
					.plusHours(23)
					.plusMinutes(59)
					.plusSeconds(59)); 
        }
        
        if (request.getMemo() != null) {
            order.setMemo(request.getMemo());
        }
        
        if (request.getWarehouseCode() != null) {
             Warehouse warehouse = warehouseRepository.findById(request.getWarehouseCode()).orElse(null);
             order.setWarehouse(warehouse);
        }
        
        if (request.getAttachmentId() != null) {
            com.buyflow.erp.Entity.Attachment attachment = attachmentRepository.findById(request.getAttachmentId())
                    .orElse(null); // 파일이 없으면 null 처리
            order.setAttachment(attachment);
        }

        // 공급업체 변경
        if (request.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new EntityNotFoundException("공급업체가 존재하지 않습니다. ID: " + request.getSupplierId()));
            order.setSupplier(supplier);
        }

        long totalSupplyAmount = 0L;
        long totalVatAmount = 0L;
        
        List<PurchaseOrderItem> itemsToSave = new ArrayList<>();

        for (PurchaseOrderDto.Item itemReq : request.getItems()) {
        	Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. ID: " + itemReq.getProductId()));

            long lineSupply = (long) (itemReq.getUnitPrice() * itemReq.getQuantity());
            long lineVat = (long) Math.floor(lineSupply * 0.1);

            totalSupplyAmount += lineSupply;
            totalVatAmount += lineVat;
            
            if (itemReq.getOrderItemId() != null) {
            	PurchaseOrderItem existingItem = order.getItems().stream()
            			.filter(item -> item.getOrderItemId().equals(itemReq.getOrderItemId()))
            			.findFirst()
            			.orElseThrow(()-> new EntityNotFoundException("수정할 발주 품목을 찾을 수 없습니다. ID: " + itemReq.getOrderItemId()));
            	
            	existingItem.setProduct(product);
                existingItem.setQuantity(itemReq.getQuantity());
                existingItem.setUnitPrice(itemReq.getUnitPrice());
            } else {
            	
            	PurchaseOrderItem item = PurchaseOrderItem.builder()
                        .purchaseOrder(order)
                        .product(product)
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .build();

                order.addItem(item);
                itemsToSave.add(item);
            }
        }
        
        Set<Long> requestItemIds = request.getItems().stream()
        		.map(PurchaseOrderDto.Item::getOrderItemId)
        		.filter(Objects::nonNull)
        		.collect(Collectors.toSet());
       
        order.getItems().removeIf(existingItem -> !requestItemIds.contains(existingItem.getOrderItemId()));
        
        if (!itemsToSave.isEmpty()) {
        	orderItemRepository.saveAll(itemsToSave);
        }
        
        double finalTotalAmount = (double) (totalSupplyAmount + totalVatAmount);
        order.setTotalAmount(finalTotalAmount);

        // 업데이트 후 저장
        PurchaseOrder updatedOrder = orderRepository.save(order);

        PurchaseOrder refreshedOrder = orderRepository.findByIdWithItems(updatedOrder.getOrderId())
                .orElse(updatedOrder);
        
        return PurchaseOrderDto.Response.from(refreshedOrder);
    }

    @Override
    @Transactional
    public PurchaseOrderDto.Response cancelOrder(Long orderId, String cancelReason) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("발주를 찾을 수 없습니다. ID: " + orderId));

        // 취소 가능 상태 체크
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("이미 취소된 발주입니다.");
        }
        if ("COMPLETED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("이미 완료된 발주는 취소할 수 없습니다.");
        }

        // 상태 변경
        order.setOrderStatus("CANCELLED");

        // 메모에 취소 사유 추가 (선택)
        String memo = order.getMemo() != null ? order.getMemo() : "";
        if (cancelReason != null && !cancelReason.trim().isEmpty()) {
            memo += (memo.isEmpty() ? "" : "\n") + "[취소 사유] " + cancelReason;
        }
        order.setMemo(memo);

        PurchaseOrder saved = orderRepository.save(order);

        return PurchaseOrderDto.Response.from(saved);
    }
    
    @Override
    public List<PurchaseOrder> getAllOrdersForExcel() {
    	return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderId"));
    }
    
}
