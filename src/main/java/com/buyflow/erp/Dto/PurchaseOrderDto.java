package com.buyflow.erp.Dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.buyflow.erp.Entity.PurchaseOrder;
import com.buyflow.erp.Entity.PurchaseOrderItem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class PurchaseOrderDto {

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SearchCondition {
		private String orderStatus;
		private String orderNo;
		private Long requestId;
		private String requestNo;
		private String reqeustNumber;
		private String supplierName;
		private String manager;
		private String contact;
		private String userName;
		private String userPhone;
		
		private int page = 0;
		private int size = 10;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class Request {
		private Long supplierId;
		private Long createdBy; // 등록시에만 사용
		private String userName;
		private String userPhone;
		private String contact;
		private LocalDateTime dueDate;
		private String orderManager;
		private String orderStatus; // 수정시에 주로 사용
		
		private String orderNo;             // 발주 번호
		private Long requestId;             // 구매 요청 ID
		private String requestNumber;       // 구매 요청 번호
		private String requestTitle;        // 구매 요청 제목
		private LocalDate expectedReceiptFrom; // 입고 예정일 (시작)
		private LocalDate expectedReceiptTo;
		private String warehouseCode;       // 입고 창고 코드
		private String memo;                // 비고
		private String manager;
		private Long attachmentId;
		

		private List<PurchaseOrderDto.Item> items;
	}
	
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class Item {
		private Long orderItemId;
		private Long productId;
		private Long quantity;
		private Double unitPrice;
		
		private String itemCode;
	    private String itemName;
	    private String specification;
	    private String unit;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class Response {
		private Long orderId;
		private String orderNo;
		
		private Long requestId;       // 구매 요청 ID
		private String requestNo;     // 구매 요청 번호 (화면 렌더링용)
		private String requestTitle;  // 구매 요청 제목 (상세 모달용)
		
		private Long supplierId;
		private String supplierName;
		private String userName;
		private String userPhone;
		private String contact;
		private Long createdBy;
		private String orderManager;
		private LocalDateTime createdAt;
		private String orderStatus;
		private LocalDateTime dueDate;
		private Double totalAmount;
		private String manager;
		private String memo;
		
		private LocalDate expectedReceiptFrom; // 입고 예정일 (시작)
		private Long attachmentId;
		private String attachmentName;
		
		private String warehouseCode;
		private String warehouseName;

		private List<PurchaseOrderDto.Item> items;

		public static Response from(PurchaseOrder order) {
		    if (order == null) return null;

		    List<PurchaseOrderDto.Item> itemDtos = new ArrayList<>();
		    if (order.getItems() != null) {
		        itemDtos = order.getItems().stream()
		            .map(item -> {
		                PurchaseOrderDto.Item.ItemBuilder builder = PurchaseOrderDto.Item.builder()
		                    .orderItemId(item.getOrderItemId())
		                    .productId(item.getProduct() != null ? item.getProduct().getProductId() : null)
		                    .quantity(item.getQuantity())
		                    .unitPrice(item.getUnitPrice());

		                // Product 정보 매핑
		                if (item.getProduct() != null) {
		                    builder
		                        .itemCode(item.getProduct().getProductNo())
		                        .itemName(item.getProduct().getProductName())
		                        .specification(item.getProduct().getSpec())
		                        .unit(item.getProduct().getUnit());
		                }
		                return builder.build();
		            })
		            .collect(Collectors.toList());
		    }

		    double checkedTotalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
		    if (checkedTotalAmount == 0.0 && order.getItems() != null && !order.getItems().isEmpty()) {
		        long calcSupply = 0L;
		        for (PurchaseOrderItem orderItem : order.getItems()) {
		            long qty = orderItem.getQuantity() != null ? orderItem.getQuantity() : 0L;
		            double price = orderItem.getUnitPrice() != null ? orderItem.getUnitPrice() : 0.0;
		            calcSupply += (long) (qty * price);
		        }
		        checkedTotalAmount = (double) (calcSupply + (long) Math.floor(calcSupply * 0.1));
		    }

		    return Response.builder()
		        .orderId(order.getOrderId())
		        .orderNo(order.getOrderNo())
		        .requestId(order.getPurchaseRequest() != null ? order.getPurchaseRequest().getRequestId() : null)
		        .requestNo(order.getPurchaseRequest() != null ? order.getPurchaseRequest().getRequestNo() : "-")
		        .requestTitle(order.getPurchaseRequest() != null ? order.getPurchaseRequest().getTitle() : "-")
		        .supplierId(order.getSupplier() != null ? order.getSupplier().getSupplierId() : null)
		        .supplierName(order.getSupplier() != null ? order.getSupplier().getSupplierName() : "-")
		        .manager(order.getSupplier() != null ? order.getSupplier().getManager() : "-")
		        .createdBy(order.getUser() != null ? order.getUser().getUserId() : null)
		        .userName(order.getUser() != null ? order.getUser().getUserName() : "-")
		        .userPhone(order.getUser() != null ? order.getUser().getPhone() : "-")
		        .createdAt(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now())
		        .orderStatus(order.getOrderStatus())
		        .dueDate(order.getDueDate())
		        .totalAmount(checkedTotalAmount)
		        .items(itemDtos)
		        .expectedReceiptFrom(order.getExpectedReceiptFrom())
		        .memo(order.getMemo())
		        .attachmentId(order.getAttachment() != null ? order.getAttachment().getAttachmentId() : null)
		        .attachmentName(order.getAttachment() != null ? order.getAttachment().getOriginalName() : null)
		        .warehouseCode(order.getWarehouse() != null ? order.getWarehouse().getWarehouseCode() : null)
		        .warehouseName(order.getWarehouse() != null ? order.getWarehouse().getWarehouseName() : "-")
		        .build();
		}
	}
	
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class ItemResponse {
		private Long requestItemId;     // 구매 요청
		private String itemCode;        // 품목 코드
		private String itemName;        // 품목명
		private String specification;   // 규격
		private Integer requestedQuantity; // 요청 수량
		private Integer orderQuantity;     // 발주 수량
		private String unit;            // 단위
		private Long unitPrice;         // 공급 단가
	}
	
	public record SummaryResponse(
			long total,
			long confirmed,
			long ordered,
			long canceled
			
			) {
	}
}