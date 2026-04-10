package io.stu.notify;

import io.stu.notify.event.NotifyEventListener;
import io.stu.notify.repository.JdbcNotifyRepository;
import io.stu.notify.repository.NotifyRepository;
import io.stu.notify.stomp.StompWebSocketHandler;
import io.stu.notify.stomp.StompWebsocketConfig;
import io.stu.notify.stomp.StompWebsocketInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Quick Notify 自动配置。
 * <p>
 * 用户可通过以下方式覆盖默认行为：
 * <ul>
 *   <li>定义自己的 {@link WebSocketMessageBrokerConfigurer} Bean 来覆盖 WebSocket 配置</li>
 *   <li>定义自己的 {@link NotifyRepository} Bean 来覆盖数据仓储</li>
 *   <li>定义自己的 {@link ChannelInterceptor} Bean 来覆盖认证逻辑</li>
 * </ul>
 */
@Slf4j
@Configuration
@AutoConfiguration
@EnableAsync
@EnableWebSocketMessageBroker
public class QuickNotifyAutoConfiguration {

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<SimpUserRegistry> userRegistryProvider;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final org.redisson.api.RedissonClient redisson;

    public QuickNotifyAutoConfiguration(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) ObjectProvider<SimpUserRegistry> userRegistryProvider,
            @Autowired(required = false) ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider,
            @Autowired(required = false) org.redisson.api.RedissonClient redisson) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.userRegistryProvider = userRegistryProvider;
        this.messagingTemplateProvider = messagingTemplateProvider;
        this.redisson = redisson;
        log.info("[QuickNotifyAutoConfiguration] 初始化完成, jdbcTemplate={}, redisson={}",
                jdbcTemplate != null, redisson != null);
    }

    @Bean
    @ConditionalOnMissingBean(NotifyRepository.class)
    public NotifyRepository notifyRepository() {
        log.info("[QuickNotifyAutoConfiguration] 创建 NotifyRepository Bean");
        return new JdbcNotifyRepository(jdbcTemplate);
    }

    @Bean
    public NotifyManager notifyManager(NotifyRepository repository) {
        log.info("[QuickNotifyAutoConfiguration] 创建 NotifyManager Bean");
        return new NotifyManager(repository, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(ChannelInterceptor.class)
    public StompWebsocketInterceptor stompWebsocketInterceptor() {
        log.info("[QuickNotifyAutoConfiguration] 创建 StompWebsocketInterceptor Bean");
        return new StompWebsocketInterceptor(userRegistryProvider);
    }

    @Bean
    public StompWebSocketHandler stompWebSocketHandler() {
        log.info("[QuickNotifyAutoConfiguration] 创建 StompWebSocketHandler Bean");
        StompWebSocketHandler handler = new StompWebSocketHandler();
        handler.setUserRegistry(userRegistryProvider.getIfAvailable());
        handler.setSimpMessagingTemplate(messagingTemplateProvider.getIfAvailable());
        handler.setRedisson(redisson);
        return handler;
    }

    @Bean
    public NotifyEventListener stompNotifyEventListener() {
        log.info("[QuickNotifyAutoConfiguration] 创建 NotifyEventListener Bean");
        NotifyEventListener listener = new NotifyEventListener();
        listener.setStompWebSocketHandler(stompWebSocketHandler());
        listener.setRedisson(redisson);
        return listener;
    }

    /**
     * WebSocket 配置。
     * <p>
     * 用户定义自己的 {@link WebSocketMessageBrokerConfigurer} Bean 时，此配置不生效。
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketMessageBrokerConfigurer.class)
    public WebSocketMessageBrokerConfigurer stompWebsocketConfig() {
        log.info("[QuickNotifyAutoConfiguration] 创建 StompWebsocketConfig Bean");
        StompWebsocketConfig config = new StompWebsocketConfig();
        // 注入拦截器
        config.setChannelInterceptor(stompWebsocketInterceptor());
        return config;
    }

    /**
     * 初始化订阅
     */
    @PostConstruct
    public void init() {
        log.info("[QuickNotifyAutoConfiguration] PostConstruct 执行");
        if (redisson != null) {
            try {
                stompNotifyEventListener().subscribeToTopic();
                log.info("[QuickNotifyAutoConfiguration] Redis 主题订阅成功");
            } catch (Exception e) {
                log.warn("[QuickNotifyAutoConfiguration] 订阅主题失败: {}", e.getMessage());
            }
        } else {
            log.warn("[QuickNotifyAutoConfiguration] Redisson 未配置，跳过 Redis 主题订阅");
        }
    }
}
