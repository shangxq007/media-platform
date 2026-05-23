package com.example.platform.delivery.spi;

import com.example.platform.delivery.domain.DeliveryProtocol;

public interface DeliveryAdapter {

    DeliveryProtocol protocol();

    ProbeResult probe(DeliveryContext context);

    DeliveryResult deliver(DeliveryContext context);

    record ProbeResult(boolean ok, String message) {
        public static ProbeResult success() {
            return new ProbeResult(true, "ok");
        }

        public static ProbeResult failure(String message) {
            return new ProbeResult(false, message);
        }
    }

    record DeliveryResult(boolean success, String remotePath, String remoteUri, long bytesTransferred, String error) {
        public static DeliveryResult ok(String remotePath, String remoteUri, long bytes) {
            return new DeliveryResult(true, remotePath, remoteUri, bytes, null);
        }

        public static DeliveryResult fail(String error) {
            return new DeliveryResult(false, null, null, 0, error);
        }
    }
}
