package com.buyflow.erp.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buyflow.erp.Entity.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findBySupplier_SupplierId(Long supplierId);

    List<PurchaseOrder> findByOrderStatus(String status);

    @Query("SELECT p FROM PurchaseOrder p " +
            "LEFT JOIN FETCH p.items i " +
            "LEFT JOIN FETCH i.product " +
            "LEFT JOIN FETCH p.supplier " +
            "LEFT JOIN FETCH p.user " + 
            "LEFT JOIN FETCH p.attachment " +
            "WHERE p.orderId = :orderId")
    Optional<PurchaseOrder> findByIdWithItems(@Param("orderId") Long orderId);

    
    @Query("SELECT DISTINCT p FROM PurchaseOrder p " +
            "LEFT JOIN p.supplier s " +
            "LEFT JOIN p.user u " +
            "LEFT JOIN p.purchaseRequest pr " +
            "WHERE (:orderNo IS NULL OR p.orderNo LIKE CONCAT('%', CONCAT(:orderNo, '%'))) " +
            "AND (:requestNumber IS NULL OR pr.requestNo LIKE CONCAT('%', :requestNumber, '%')) " +
            "AND (:supplierName IS NULL OR s.supplierName LIKE CONCAT('%', CONCAT(:supplierName, '%'))) " +
            "AND (:userName IS NULL OR u.userName LIKE CONCAT('%', CONCAT(:userName, '%'))) " +
            "AND (:status IS NULL OR p.orderStatus = :status) " +
            "AND p.orderStatus NOT IN ('DRAFT')" +
            "ORDER BY p.createdAt DESC, p.orderId DESC")
    Page<PurchaseOrder> searchOrdersAdvanced(
            @Param("orderNo") String orderNo,
            @Param("requestNumber") String requestNumber,
            @Param("supplierName") String supplierName,
            @Param("userName") String userName,
            @Param("status") String status,
            Pageable pageable
    );
    
    @Query("SELECT MAX(p.orderNo) FROM PurchaseOrder p WHERE p.orderNo LIKE :prefix")
    String findMaxOrderNoByToday(@Param("prefix") String prefix);

    boolean existsByAttachment_AttachmentId(Long attachmentId);
    
}
