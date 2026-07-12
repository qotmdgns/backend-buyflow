package com.buyflow.erp.Controller;

import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Service.AttachmentAuthorizationService;
import com.buyflow.erp.Service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentControllerTest {

    @Test
    void accessDeniedIsPropagatedAsForbiddenInsteadOfBeingConvertedToServerError() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentAuthorizationService authorizationService = mock(AttachmentAuthorizationService.class);
        Authentication authentication = mock(Authentication.class);
        Attachment attachment = new Attachment();
        when(attachmentService.getAttachmentInfo(1L)).thenReturn(attachment);
        doThrow(new AccessDeniedException("Forbidden"))
                .when(authorizationService).assertCanDownload(authentication, attachment);

        AttachmentController controller = new AttachmentController(attachmentService, authorizationService);

        assertThatThrownBy(() -> controller.downloadAttachment(1L, authentication))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void downloadMappingDoesNotDuplicateApiContextPath() throws NoSuchMethodException {
        GetMapping mapping = AttachmentController.class
                .getMethod("downloadAttachment", Long.class, Authentication.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/attachments/download/{attachmentId}");
    }
}
