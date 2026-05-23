package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.spi.DeliveryAdapter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DeliveryAdapterRegistry {

    private final Map<DeliveryProtocol, DeliveryAdapter> adapters;

    public DeliveryAdapterRegistry(List<DeliveryAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(DeliveryAdapter::protocol, Function.identity(), (a, b) -> a));
    }

    public Optional<DeliveryAdapter> get(DeliveryProtocol protocol) {
        return Optional.ofNullable(adapters.get(protocol));
    }
}
