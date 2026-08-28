package com.educloud.ai.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久化集成测试（规格 §7）：真实 MySQL 上验证建表迁移、消息升序与软删语义。
 * 默认 skipITs=true；VM 上执行：mvn -f educloud-backend/pom.xml -pl educloud-ai -Pintegration verify
 *
 * <p>相对任务文本稿的两处最小修复（本地无 Docker 已预验证，避免 VM 部署返工）：
 * ① 原稿 JWKS n="x" 不是合法 RSA 模数，JwksLoader.validateKey 的 toRSAPublicKey() 会拒绝，
 * 上下文启动即失败——改为运行时生成真实 2048 位 RSA 公钥 JWKS（writeItJwks）；
 * ② 原稿 file.toUri().toString() 在 Windows 上生成 file:///C:/...，JwksLoader 去掉 file: 前缀后
 * 剩 //C:/... 会被 FileSystemResource 当 UNC 路径拒绝——改为 "file:" + 绝对路径
 * （与 application.yml 的 file:/tmp/... 生产写法一致，Linux/Windows 均可用）。</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = AiChatPersistenceIT.DbInitializer.class)
@Tag("integration")
class AiChatPersistenceIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("educloud_ai")
            .withUsername("ai_app")
            .withPassword("ai_app_pw")
            .withInitScript("ai/V001__ai.sql");

    static class DbInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL.getUsername(),
                    "spring.datasource.password=" + MYSQL.getPassword(),
                    "spring.data.redis.host=localhost",
                    "educloud.ai.provider.api-key=it-key",
                    "educloud.ai.jwt.jwks-location="
                            + writeItJwks()).applyTo(context.getEnvironment());
        }
    }

    /**
     * 写出一个最小合法 RSA 公钥 JWKS 到临时文件，返回 jwks-location。
     * 仅用于构造 Decoder Bean（AiProviderConfig 要求 api-key 非空、JwksLoader 要求至少一个合法
     * RSA 公钥），测试不发真实请求；包级可见以便无 Docker 环境复用同一代码路径做本地验证。
     */
    static String writeItJwks() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            RSAPublicKey publicKey = (RSAPublicKey) generator.generateKeyPair().getPublic();
            String jwk = new RSAKey.Builder(publicKey)
                    .keyID("it")
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build()
                    .toJSONString();
            Path file = Files.createTempFile("it-jwks", ".json");
            Files.writeString(file, "{\"keys\":[" + jwk + "]}");
            return "file:" + file.toAbsolutePath();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot prepare IT JWKS file", exception);
        }
    }

    @Autowired
    private AiConversationMapper conversationMapper;
    @Autowired
    private AiMessageMapper messageMapper;

    @Test
    void conversationAndMessagesRoundTripWithOrderingAndSoftDelete() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setStudentId(2001L);
        conversation.setTitle("集成测试会话");
        conversation.setMessageCount(0);
        conversation.setDeleted(0);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        assertThat(conversation.getId()).isNotNull().isPositive(); // 雪花 ID 已回填

        for (int i = 1; i <= 3; i++) {
            AiMessageEntity row = new AiMessageEntity();
            row.setConversationId(conversation.getId());
            row.setRole(i % 2 == 1 ? "user" : "assistant");
            row.setContent("消息" + i);
            row.setStatus("OK");
            messageMapper.insert(row);
        }

        List<AiMessageEntity> ascending = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessageEntity>()
                        .eq(AiMessageEntity::getConversationId, conversation.getId())
                        .orderByAsc(AiMessageEntity::getId));
        assertThat(ascending).extracting(AiMessageEntity::getContent)
                .containsExactly("消息1", "消息2", "消息3");

        conversation.setDeleted(1);
        conversationMapper.updateById(conversation);
        Long visible = conversationMapper.selectCount(new LambdaQueryWrapper<AiConversationEntity>()
                .eq(AiConversationEntity::getStudentId, 2001L)
                .eq(AiConversationEntity::getDeleted, 0));
        assertThat(visible).isZero();
    }
}
