package com.educloud.analytics.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDomainEvent {
    private String eventId;
    private String eventType; // e.g. UserRegistered, UserLoggedIn
    private Long userId;
    private String username;
    private String userType;
    private LocalDateTime occurredAt;
}
