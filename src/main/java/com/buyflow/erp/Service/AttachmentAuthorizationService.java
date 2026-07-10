package com.buyflow.erp.Service;

import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.PurchaseOrderRepository;
import com.buyflow.erp.Repository.PurchaseRequestRepository;
import com.buyflow.erp.Repository.ReceiptRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentAuthorizationService {

    private final UserRepository userRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReceiptRepository receiptRepository;

    public void assertCanDownload(Authentication authentication, Attachment attachment) {
        if (authentication == null || !authentication.isAuthenticated() || attachment == null) {
            throw new AccessDeniedException("권한이 없습니다.");
        }

        Set<String> authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (authorities.contains(PermissionCodes.ROLE_ADMIN)) {
            return;
        }

        Users currentUser = userRepository.findByLoginId(authentication.getName()).orElse(null);
        if (currentUser != null
                && attachment.getUser() != null
                && currentUser.getUserId().equals(attachment.getUser().getUserId())) {
            return;
        }

        if (purchaseOrderRepository.existsByAttachment_AttachmentId(attachment.getAttachmentId())
                && hasAny(authorities, PermissionCodes.PURCHASE_ORDERS_READ, PermissionCodes.PURCHASE_ORDERS_WRITE)) {
            return;
        }

        Long requestId = attachment.getRequestId();
        if (requestId != null && requestId > 0
                && purchaseRequestRepository.existsById(requestId)
                && hasAny(authorities, PermissionCodes.PURCHASE_REQUESTS_READ, PermissionCodes.PURCHASE_REQUESTS_WRITE)) {
            return;
        }

        if (requestId != null && requestId < 0
                && receiptRepository.existsById(Math.abs(requestId))
                && hasAny(authorities, PermissionCodes.RECEIPTS_READ, PermissionCodes.RECEIPTS_WRITE)) {
            return;
        }

        throw new AccessDeniedException("권한이 없습니다.");
    }

    private boolean hasAny(Set<String> authorities, String... required) {
        for (String authority : required) {
            if (authorities.contains(authority)) {
                return true;
            }
        }
        return false;
    }
}
