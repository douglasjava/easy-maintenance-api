package com.brainbyte.easy_maintenance.infrastructure.notification.web;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.InAppNotificationResponse;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.InAppNotificationService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/notifications")
@Tag(name = "Notificações", description = "Centro de notificações in-app do usuário")
public class NotificationController {

    private final InAppNotificationService inAppNotificationService;
    private final AuthenticationService authenticationService;

    @GetMapping
    @Operation(summary = "Lista as últimas 20 notificações do usuário com contagem de não lidas")
    public Map<String, Object> list() {
        User user = authenticationService.getCurrentUser();
        List<InAppNotificationResponse> notifications = inAppNotificationService.listForUser(user.getId());
        long unreadCount = inAppNotificationService.countUnread(user.getId());
        return Map.of(
                "notifications", notifications,
                "unreadCount", unreadCount
        );
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca uma notificação como lida")
    public void markAsRead(@PathVariable Long id) {
        User user = authenticationService.getCurrentUser();
        inAppNotificationService.markAsRead(user.getId(), id);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca todas as notificações do usuário como lidas")
    public void markAllRead() {
        User user = authenticationService.getCurrentUser();
        inAppNotificationService.markAllRead(user.getId());
    }
}
