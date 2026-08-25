package com.educloud.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailChannelStatusResponse {
    private String provider;
    private String host;
    private Integer port;
    private String username;
    private String from;
    private boolean sslEnabled;
    private boolean passwordConfigured;
}
