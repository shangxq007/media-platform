package com.example.platform.notification.infrastructure;

import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import com.example.platform.notification.domain.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationProviderRouter {
    private static final Logger log = LoggerFactory.getLogger(NotificationProviderRouter.class);

    private final Map<String, NotificationProvider> providerByCode;
    private final NovuNotificationProvider novuProvider;
    private final MockNotificationProvider localProvider;

    public NotificationProviderRouter(List<NotificationProvider> providers,
            @Autowired(required = false) NovuNotificationProvider novuProvider,
            MockNotificationProvider localProvider) {
        this.providerByCode = providers.stream()
                .collect(Collectors.toMap(NotificationProvider::providerCode, Function.identity()));
        this.novuProvider = novuProvider;
        this.localProvider = localProvider;
    }

    public Optional<NotificationProvider> findByCode(String providerCode) {
        return Optional.ofNullable(providerByCode.get(providerCode));
    }

    public DeliveryResult route(DeliveryCommand command, String channel) {
        if (novuProvider.isEnabled()) {
            log.debug("NotificationProviderRouter: routing to novu for channel={}", channel);
            return novuProvider.send(command);
        }
        NotificationProvider provider = findByChannel(channel);
        if (provider != null) {
            log.debug("NotificationProviderRouter: routing to provider={} for channel={}", provider.providerCode(), channel);
            return provider.send(command);
        }
        log.debug("NotificationProviderRouter: falling back to local provider for channel={}", channel);
        return localProvider.send(command);
    }

    private NotificationProvider findByChannel(String channel) {
        if (channel == null) return null;
        for (NotificationProvider provider : providerByCode.values()) {
            if (channel.equalsIgnoreCase(provider.channel())) {
                return provider;
            }
        }
        return providerByCode.get(channel);
    }
}
