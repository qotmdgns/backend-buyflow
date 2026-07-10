package com.buyflow.erp.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.buyflow.erp.Dto.PurchaseRequestDto;
import com.buyflow.erp.Dto.ReceiptDto;
import com.buyflow.erp.Dto.StockDto;
import com.buyflow.erp.Dto.StockHistoryResponseDto;
import com.buyflow.erp.Entity.ExcelExportHistory;
import com.buyflow.erp.Entity.Product;
import com.buyflow.erp.Entity.PurchaseOrder;
import com.buyflow.erp.Entity.Supplier;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.ExcelExportHistoryRepository;
import com.buyflow.erp.Repository.ProductRepository;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExcelServiceImpl implements ExcelService {

	private final PurchaseOrderService purchaseOrderService;
	private final ReceiptService receiptService;
	private final StockService stockService;
	private final StockHistoryService stockHistoryService;
	private final ExcelExportHistoryRepository historyRepository;
	private final SupplierService supplierService;
	private final PurchaseRequestService purchaseRequestService;
	private final ProductRepository productRepository;

    @Override
    public void exportExcel(String target, Users user, HttpServletResponse response) throws IOException {
        long rowCount = 0;
        String status = "SUCCESS";

        try {
            System.setProperty(
                    "javax.xml.parsers.DocumentBuilderFactory",
                    "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
            System.setProperty(
                    "javax.xml.parsers.SAXParserFactory",
                    "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");

            try (Workbook workbook = new XSSFWorkbook()) {
                rowCount = drawTargetSheet(target, workbook);

                String encodedFileName = java.net.URLEncoder
                        .encode(resolveFileName(target), StandardCharsets.UTF_8.toString())
                        .replaceAll("\\+", "%20");

                response.setContentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader(
                        "Content-Disposition",
                        "attachment; filename=\"" + encodedFileName + "\"");

                workbook.write(response.getOutputStream());
            }
        } catch (Exception e) {
            status = "FAILED";
            throw e;
        } finally {
            saveHistory(target, user, status, rowCount);
        }
    }

    private long drawTargetSheet(String target, Workbook workbook) {
        if ("orders".equalsIgnoreCase(target)) {
            return drawOrderSheet(workbook);
        }

        if ("inventories".equalsIgnoreCase(target)) {
            return drawInventorySheet(workbook);
        }

        if ("stock-history".equalsIgnoreCase(target)) {
            return drawStockHistorySheet(workbook);
        }

        if ("receipts".equalsIgnoreCase(target)) {
            return drawReceiptSheet(workbook);
        }

        if ("suppliers".equalsIgnoreCase(target)) {
            return drawSupplierSheet(workbook);
        }

        if ("purchase-requests".equalsIgnoreCase(target)) {
            return drawPurchaseRequestSheet(workbook);
        }

        if ("products".equalsIgnoreCase(target)) {
            return drawProductSheet(workbook);
        }

        workbook.createSheet("Data");
        return 0;
    }

    private String resolveFileName(String target) {
        if ("orders".equalsIgnoreCase(target)) {
            return "purchase-orders.xlsx";
        }

        if ("inventories".equalsIgnoreCase(target)) {
            return "stock.xlsx";
        }

        if ("stock-history".equalsIgnoreCase(target)) {
            return "stock-history.xlsx";
        }

        if ("receipts".equalsIgnoreCase(target)) {
            return "receipts.xlsx";
        }

        if ("suppliers".equalsIgnoreCase(target)) {
            return "suppliers.xlsx";
        }

        if ("purchase-requests".equalsIgnoreCase(target)) {
            return "purchase-requests.xlsx";
        }

        if ("products".equalsIgnoreCase(target)) {
            return "products.xlsx";
        }

        return "exported-data.xlsx";
    }

    private void saveHistory(String target, Users user, String status, long rowCount) {
        ExcelExportHistory history = new ExcelExportHistory();
        history.setExportType(target.toUpperCase());
        history.setStatus(status);
        history.setCreatedAt(LocalDateTime.now());
        history.setDownloadRowCount(rowCount);
        history.setUser(user);

        historyRepository.save(history);
    }


//	private long drawOrderSheet(Workbook workbook) {
//		Sheet sheet = workbook.createSheet("발주 내역");
//		List<PurchaseOrder> orders = purchaseOrderService.getAllOrdersForExcel();
//
//		Row headerRow = sheet.createRow(0);
//		headerRow.createCell(0).setCellValue("발주 번호");
//		headerRow.createCell(1).setCellValue("공급업체");
//		headerRow.createCell(2).setCellValue("총 금액");
//		headerRow.createCell(3).setCellValue("상태");
//
//		int rowNum = 1;
//		for (PurchaseOrder order : orders) {
//			Row row = sheet.createRow(rowNum++);
//			row.createCell(0).setCellValue(order.getOrderNo());
//			row.createCell(1).setCellValue(order.getSupplier() != null ? order.getSupplier().getSupplierName() : "-");
//			row.createCell(2).setCellValue(order.getTotalAmount() != null ? order.getTotalAmount() : 0.0);
//			row.createCell(3).setCellValue(order.getOrderStatus());
//		}
//		return orders.size();
//	}
//
//	private long drawInventorySheet(Workbook workbook) {
//
//		Sheet sheet = workbook.createSheet("재고 현황");
//
//		List<StockDto> stocks = stockService.findAllStocks();
//
//		Row headerRow = sheet.createRow(0);
//
//		headerRow.createCell(0).setCellValue("품목코드");
//		headerRow.createCell(1).setCellValue("품목명");
//		headerRow.createCell(2).setCellValue("카테고리");
//		headerRow.createCell(3).setCellValue("창고");
//		headerRow.createCell(4).setCellValue("현재재고");
//		headerRow.createCell(5).setCellValue("안전재고");
//		headerRow.createCell(6).setCellValue("단위");
//
//		int rowNum = 1;
//
//		for (StockDto stock : stocks) {
//
//			Row row = sheet.createRow(rowNum++);
//
//			row.createCell(0).setCellValue(stock.getItemCode());
//			row.createCell(1).setCellValue(stock.getItemName());
//			row.createCell(2).setCellValue(stock.getCategory());
//			row.createCell(3).setCellValue(stock.getWarehouseName());
//			row.createCell(4).setCellValue(stock.getCurrentStock());
//			row.createCell(5).setCellValue(stock.getSafetyStock());
//			row.createCell(6).setCellValue(stock.getUnit());
//		}
//
//		return stocks.size();
//	}
//
//	private long drawStockHistorySheet(Workbook workbook) {
//
//		Sheet sheet = workbook.createSheet("재고 이력");
//
//		List<StockHistoryResponseDto> histories = stockHistoryService.getStockHistory();
//
//		Row headerRow = sheet.createRow(0);
//
//		headerRow.createCell(0).setCellValue("발생일시");
//		headerRow.createCell(1).setCellValue("변경유형");
//		headerRow.createCell(2).setCellValue("품목코드");
//		headerRow.createCell(3).setCellValue("품목명");
//		headerRow.createCell(4).setCellValue("창고");
//		headerRow.createCell(5).setCellValue("변경수량");
//		headerRow.createCell(6).setCellValue("이전재고");
//		headerRow.createCell(7).setCellValue("현재재고");
//		headerRow.createCell(8).setCellValue("사유");
//		headerRow.createCell(9).setCellValue("처리자");
//
//		int rowNum = 1;
//
//		for (StockHistoryResponseDto history : histories) {
//
//			Row row = sheet.createRow(rowNum++);
//
//			row.createCell(0).setCellValue(
//					history.getOccurredAt() != null ? history.getOccurredAt() : "");
//
//			row.createCell(1).setCellValue(
//					history.getMovementType() != null ? history.getMovementType() : "");
//
//			row.createCell(2).setCellValue(
//					history.getItemCode() != null ? history.getItemCode() : "");
//
//			row.createCell(3).setCellValue(
//					history.getItemName() != null ? history.getItemName() : "");
//
//			row.createCell(4).setCellValue(
//					history.getWarehouseName() != null ? history.getWarehouseName() : "");
//
//			row.createCell(5).setCellValue(
//					history.getQuantity() != null ? history.getQuantity() : 0);
//
//			row.createCell(6).setCellValue(
//					history.getBeforeStock() != null ? history.getBeforeStock() : 0);
//
//			row.createCell(7).setCellValue(
//					history.getAfterStock() != null ? history.getAfterStock() : 0);
//
//			row.createCell(8).setCellValue(
//					history.getReason() != null ? history.getReason() : "");
//
//			row.createCell(9).setCellValue(
//					history.getProcessedBy() != null ? history.getProcessedBy() : "");
//		}
//
//		return histories.size();
//	}
//
//	private long drawReceiptSheet(Workbook workbook) {
//
//		Sheet sheet = workbook.createSheet("입고 관리");
//
//		ReceiptDto.PageResponse<ReceiptDto.ListResponse> response = receiptService.searchReceipts(
//				null,
//				null,
//				null,
//				null,
//				null,
//				null,
//				null,
//				null,
//				null,
//				1,
//				99999);
//
//		List<ReceiptDto.ListResponse> receipts = response.getItems();
//
//		Row headerRow = sheet.createRow(0);
//
//		headerRow.createCell(0).setCellValue("발주번호");
//		headerRow.createCell(1).setCellValue("공급업체");
//		headerRow.createCell(2).setCellValue("발주일");
//		headerRow.createCell(3).setCellValue("입고예정일");
//		headerRow.createCell(4).setCellValue("창고");
//		headerRow.createCell(5).setCellValue("품목수");
//		headerRow.createCell(6).setCellValue("발주수량");
//		headerRow.createCell(7).setCellValue("입고수량");
//		headerRow.createCell(8).setCellValue("미입고수량");
//		headerRow.createCell(9).setCellValue("상태");
//
//		int rowNum = 1;
//
//		for (ReceiptDto.ListResponse receipt : receipts) {
//
//			Row row = sheet.createRow(rowNum++);
//
//			row.createCell(0).setCellValue(receipt.getOrderNumber());
//			row.createCell(1).setCellValue(receipt.getSupplierName());
//			row.createCell(2).setCellValue(receipt.getOrderedAt());
//			row.createCell(3).setCellValue(receipt.getExpectedReceiptAt());
//			row.createCell(4).setCellValue(receipt.getWarehouseName());
//			row.createCell(5).setCellValue(receipt.getItemCount());
//			row.createCell(6).setCellValue(receipt.getOrderQuantity());
//			row.createCell(7).setCellValue(receipt.getReceivedQuantity());
//			row.createCell(8).setCellValue(receipt.getRemainingQuantity());
//			row.createCell(9).setCellValue(receipt.getStatus());
//		}
//
//		return receipts.size();
//	}
//	private long drawSupplierSheet(Workbook workbook) {
//    Sheet sheet = workbook.createSheet("공급업체 목록");
//
//    List<Supplier> suppliers = supplierService.findAllForExcel();
//
//    Row headerRow = sheet.createRow(0);
//    headerRow.createCell(0).setCellValue("공급업체 코드");
//    headerRow.createCell(1).setCellValue("공급업체명");
//    headerRow.createCell(2).setCellValue("사업자등록번호");
//    headerRow.createCell(3).setCellValue("담당자");
//    headerRow.createCell(4).setCellValue("연락처");
//    headerRow.createCell(5).setCellValue("이메일");
//    headerRow.createCell(6).setCellValue("주소");
//    headerRow.createCell(7).setCellValue("거래상태");
//    headerRow.createCell(8).setCellValue("등록일");
//
//    int rowNum = 1;
//
//    for (Supplier supplier : suppliers) {
//        Row row = sheet.createRow(rowNum++);
//
//        row.createCell(0).setCellValue(valueOrDash(supplier.getSupplierCode()));
//        row.createCell(1).setCellValue(valueOrDash(supplier.getSupplierName()));
//        row.createCell(2).setCellValue(valueOrDash(supplier.getBusinessNumber()));
//        row.createCell(3).setCellValue(valueOrDash(supplier.getManager()));
//        row.createCell(4).setCellValue(valueOrDash(supplier.getContact()));
//        row.createCell(5).setCellValue(valueOrDash(supplier.getEmail()));
//        row.createCell(6).setCellValue(valueOrDash(supplier.getAddress()));
//        row.createCell(7).setCellValue(toTradeStatusLabel(supplier.getTradeStatus()));
//        row.createCell(8).setCellValue(
//                supplier.getCreatedAt() != null
//                        ? supplier.getCreatedAt().toLocalDate().toString()
//                        : "-"
//        );
//    }
//
//    for (int i = 0; i <= 8; i++) {
//        sheet.autoSizeColumn(i);
//    }
//
//    return suppliers.size();
//}
//
//  private long drawPurchaseRequestSheet(Workbook workbook) {
//    Sheet sheet = workbook.createSheet("구매요청 목록");
//
//    List<PurchaseRequestDto.ListResponse> requests =
//    		purchaseRequestService.getAllPurchaseRequestRowsForExcel();
//
//    String[] headers = {
//            "요청 번호",
//            "요청 제목",
//            "요청자",
//            "요청 부서",
//            "요청일",
//            "수정일",
//            "희망 입고일",
//            "품목 수",
//            "총 요청 금액",
//            "우선순위",
//            "상태"
//    };
//
//    Row headerRow = sheet.createRow(0);
//    for (int i = 0; i < headers.length; i++) {
//        headerRow.createCell(i).setCellValue(headers[i]);
//    }
//
//    int rowNum = 1;
//
//    for (PurchaseRequestDto.ListResponse request : requests) {
//        Row row = sheet.createRow(rowNum++);
//
//        row.createCell(0).setCellValue(valueOrDash(request.requestNumber()));
//        row.createCell(1).setCellValue(valueOrDash(request.title()));
//        row.createCell(2).setCellValue(valueOrDash(request.requester()));
//        row.createCell(3).setCellValue(valueOrDash(request.department()));
//        row.createCell(4).setCellValue(valueOrDash(request.requestedAt()));
//        row.createCell(5).setCellValue(valueOrDash(request.updatedAt()));
//        row.createCell(6).setCellValue(valueOrDash(request.desiredReceiptAt()));
//        row.createCell(7).setCellValue(request.itemCount());
//        row.createCell(8).setCellValue(
//                request.totalAmount() != null ? request.totalAmount().doubleValue() : 0
//        );
//        row.createCell(9).setCellValue(valueOrDash(request.priority()));
//        row.createCell(10).setCellValue(valueOrDash(request.status()));
//    }
//
//    for (int i = 0; i < headers.length; i++) {
//        sheet.autoSizeColumn(i);
//    }
//
//    return requests.size();
//}

private String valueOrDash(String value) {
    return value != null && !value.isBlank() ? value : "-";
}

private String toTradeStatusLabel(String tradeStatus) {
    if ("ACTIVE".equalsIgnoreCase(tradeStatus)) {
        return "거래중";
    }

    if ("STOPPED".equalsIgnoreCase(tradeStatus) || "INACTIVE".equalsIgnoreCase(tradeStatus)) {
        return "거래중지";
    }

    return valueOrDash(tradeStatus);
}

    private long drawOrderSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Purchase Orders");
        List<PurchaseOrder> orders = purchaseOrderService.getAllOrdersForExcel();

        writeHeader(sheet, "Order No", "Supplier", "Total Amount", "Status");

        int rowNum = 1;
        for (PurchaseOrder order : orders) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(order.getOrderNo()));
            row.createCell(1).setCellValue(
                    order.getSupplier() != null
                            ? valueOrDash(order.getSupplier().getSupplierName())
                            : "-");
            row.createCell(2).setCellValue(order.getTotalAmount() != null ? order.getTotalAmount() : 0.0);
            row.createCell(3).setCellValue(valueOrDash(order.getOrderStatus()));
        }

        autoSize(sheet, 4);
        return orders.size();
    }

    private long drawInventorySheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Stock");
        List<StockDto> stocks = stockService.findAllStocks();

        writeHeader(sheet, "Item Code", "Item Name", "Category", "Warehouse", "Current Stock", "Safety Stock", "Unit");

        int rowNum = 1;
        for (StockDto stock : stocks) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(stock.getItemCode()));
            row.createCell(1).setCellValue(valueOrDash(stock.getItemName()));
            row.createCell(2).setCellValue(valueOrDash(stock.getCategory()));
            row.createCell(3).setCellValue(valueOrDash(stock.getWarehouseName()));
            row.createCell(4).setCellValue(stock.getCurrentStock());
            row.createCell(5).setCellValue(stock.getSafetyStock());
            row.createCell(6).setCellValue(valueOrDash(stock.getUnit()));
        }

        autoSize(sheet, 7);
        return stocks.size();
    }

    private long drawStockHistorySheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Stock History");
        List<StockHistoryResponseDto> histories = stockHistoryService.getStockHistory();

        writeHeader(
                sheet,
                "Occurred At",
                "Movement Type",
                "Item Code",
                "Item Name",
                "Warehouse",
                "Quantity",
                "Before Stock",
                "After Stock",
                "Reason",
                "Processed By");

        int rowNum = 1;
        for (StockHistoryResponseDto history : histories) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(history.getOccurredAt()));
            row.createCell(1).setCellValue(valueOrDash(history.getMovementType()));
            row.createCell(2).setCellValue(valueOrDash(history.getItemCode()));
            row.createCell(3).setCellValue(valueOrDash(history.getItemName()));
            row.createCell(4).setCellValue(valueOrDash(history.getWarehouseName()));
            row.createCell(5).setCellValue(history.getQuantity() != null ? history.getQuantity() : 0);
            row.createCell(6).setCellValue(history.getBeforeStock() != null ? history.getBeforeStock() : 0);
            row.createCell(7).setCellValue(history.getAfterStock() != null ? history.getAfterStock() : 0);
            row.createCell(8).setCellValue(valueOrDash(history.getReason()));
            row.createCell(9).setCellValue(valueOrDash(history.getProcessedBy()));
        }

        autoSize(sheet, 10);
        return histories.size();
    }

    private long drawReceiptSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Receipts");
        ReceiptDto.PageResponse<ReceiptDto.ListResponse> response = receiptService.searchReceipts(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                99999);

        List<ReceiptDto.ListResponse> receipts = response.getItems();

        writeHeader(
                sheet,
                "Order No",
                "Supplier",
                "Ordered At",
                "Expected Receipt At",
                "Warehouse",
                "Item Count",
                "Order Quantity",
                "Received Quantity",
                "Remaining Quantity",
                "Status");

        int rowNum = 1;
        for (ReceiptDto.ListResponse receipt : receipts) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(receipt.getOrderNumber()));
            row.createCell(1).setCellValue(valueOrDash(receipt.getSupplierName()));
            row.createCell(2).setCellValue(valueOrDash(receipt.getOrderedAt()));
            row.createCell(3).setCellValue(valueOrDash(receipt.getExpectedReceiptAt()));
            row.createCell(4).setCellValue(valueOrDash(receipt.getWarehouseName()));
            row.createCell(5).setCellValue(receipt.getItemCount());
            row.createCell(6).setCellValue(receipt.getOrderQuantity());
            row.createCell(7).setCellValue(receipt.getReceivedQuantity());
            row.createCell(8).setCellValue(receipt.getRemainingQuantity());
            row.createCell(9).setCellValue(valueOrDash(receipt.getStatus()));
        }

        autoSize(sheet, 10);
        return receipts.size();
    }

    private long drawSupplierSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Suppliers");
        List<Supplier> suppliers = supplierService.findAllForExcel();

        writeHeader(
                sheet,
                "Supplier Code",
                "Supplier Name",
                "Business Number",
                "Manager",
                "Contact",
                "Email",
                "Address",
                "Trade Status",
                "Created At");

        int rowNum = 1;
        for (Supplier supplier : suppliers) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(supplier.getSupplierCode()));
            row.createCell(1).setCellValue(valueOrDash(supplier.getSupplierName()));
            row.createCell(2).setCellValue(valueOrDash(supplier.getBusinessNumber()));
            row.createCell(3).setCellValue(valueOrDash(supplier.getManager()));
            row.createCell(4).setCellValue(valueOrDash(supplier.getContact()));
            row.createCell(5).setCellValue(valueOrDash(supplier.getEmail()));
            row.createCell(6).setCellValue(valueOrDash(supplier.getAddress()));
            row.createCell(7).setCellValue(valueOrDash(supplier.getTradeStatus()));
            row.createCell(8).setCellValue(
                    supplier.getCreatedAt() != null
                            ? supplier.getCreatedAt().toLocalDate().toString()
                            : "-");
        }

        autoSize(sheet, 9);
        return suppliers.size();
    }

    private long drawPurchaseRequestSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Purchase Requests");
        List<PurchaseRequestDto.ListResponse> requests =
                purchaseRequestService.getAllPurchaseRequestRowsForExcel();

        writeHeader(
                sheet,
                "Request No",
                "Title",
                "Requester",
                "Department",
                "Requested At",
                "Updated At",
                "Desired Receipt At",
                "Item Count",
                "Total Amount",
                "Priority",
                "Status");

        int rowNum = 1;
        for (PurchaseRequestDto.ListResponse request : requests) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(request.requestNumber()));
            row.createCell(1).setCellValue(valueOrDash(request.title()));
            row.createCell(2).setCellValue(valueOrDash(request.requester()));
            row.createCell(3).setCellValue(valueOrDash(request.department()));
            row.createCell(4).setCellValue(valueOrDash(request.requestedAt()));
            row.createCell(5).setCellValue(valueOrDash(request.updatedAt()));
            row.createCell(6).setCellValue(valueOrDash(request.desiredReceiptAt()));
            row.createCell(7).setCellValue(request.itemCount());
            row.createCell(8).setCellValue(
                    request.totalAmount() != null ? request.totalAmount().doubleValue() : 0);
            row.createCell(9).setCellValue(valueOrDash(request.priority()));
            row.createCell(10).setCellValue(valueOrDash(request.status()));
        }

        autoSize(sheet, 11);
        return requests.size();
    }

    private long drawProductSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Products");
        List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.DESC, "productId"));

        writeHeader(
                sheet,
                "Product No",
                "Product Name",
                "Supplier",
                "Category",
                "Spec",
                "Unit",
                "Unit Price",
                "Use Yn",
                "Created At",
                "Updated At");

        int rowNum = 1;
        for (Product product : products) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrDash(product.getProductNo()));
            row.createCell(1).setCellValue(valueOrDash(product.getProductName()));
            row.createCell(2).setCellValue(valueOrDash(product.getCompanyName()));
            row.createCell(3).setCellValue(valueOrDash(product.getCategoryName()));
            row.createCell(4).setCellValue(valueOrDash(product.getSpec()));
            row.createCell(5).setCellValue(valueOrDash(product.getUnit()));
            row.createCell(6).setCellValue(product.getUnitPrice() != null ? product.getUnitPrice() : 0L);
            row.createCell(7).setCellValue(valueOrDash(product.getUseYn()));
            row.createCell(8).setCellValue(product.getCreatedAt() != null ? product.getCreatedAt().toString() : "-");
            row.createCell(9).setCellValue(product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : "-");
        }

        autoSize(sheet, 10);
        return products.size();
    }

    private void writeHeader(Sheet sheet, String... headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String valueOrDash(Object value) {
        if (value == null) {
            return "-";
        }

        String text = String.valueOf(value);
        return text.isBlank() ? "-" : text;
    }
}
