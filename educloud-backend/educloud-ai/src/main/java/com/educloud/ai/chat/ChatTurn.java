package com.educloud.ai.chat;

/** 发给模型的一条消息；role ∈ system/user/assistant。 */
public record ChatTurn(String role, String content) {
}
