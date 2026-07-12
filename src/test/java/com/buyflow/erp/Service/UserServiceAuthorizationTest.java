package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Dto.UserUpdateRequest;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Repository.AuthUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAuthorizationTest {

    @Mock
    private AuthUserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void nonAdminCannotReadAnotherUserThroughGeneralUserApi() {
        User currentUser = user(1L, "manager", "Sales");
        User otherUser = user(2L, "other", "Logistics");
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(userRepository.findByLoginId("manager")).thenReturn(Optional.of(currentUser));

        assertThatThrownBy(() -> userService.findById(2L, "manager", false))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void nonAdminCannotUpdateAnotherUserThroughGeneralUserApi() {
        User currentUser = user(1L, "manager", "Sales");
        User otherUser = user(2L, "other", "Logistics");
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(userRepository.findByLoginId("manager")).thenReturn(Optional.of(currentUser));

        UserUpdateRequest request = new UserUpdateRequest(
                "Changed Name", "changed@example.com", "010-0000-0000", null, null, null
        );

        assertThatThrownBy(() -> userService.update(2L, request, "manager", false))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void nonAdminCanUpdateOwnBasicProfileButNotDepartment() {
        User currentUser = user(1L, "user", "Sales");
        currentUser.setPositionName("Staff");
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findByLoginId("user")).thenReturn(Optional.of(currentUser));

        UserUpdateRequest request = new UserUpdateRequest(
                "New Name", "new@example.com", "010-1111-2222", "Logistics", "Manager", null
        );

        userService.update(1L, request, "user", false);

        assertThat(currentUser.getUserName()).isEqualTo("New Name");
        assertThat(currentUser.getEmail()).isEqualTo("new@example.com");
        assertThat(currentUser.getPhone()).isEqualTo("010-1111-2222");
        assertThat(currentUser.getDepartmentName()).isEqualTo("Sales");
        assertThat(currentUser.getPositionName()).isEqualTo("Staff");
    }

    private User user(Long userId, String loginId, String departmentName) {
        User user = new User();
        user.setUserId(userId);
        user.setLoginId(loginId);
        user.setUserName(loginId);
        user.setDepartmentName(departmentName);
        return user;
    }
}
