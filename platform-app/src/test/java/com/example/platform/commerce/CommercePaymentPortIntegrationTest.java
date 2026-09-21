package com.example.platform.commerce;

import com.example.platform.commerce.api.checkout.*;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.app.CheckoutOrchestrator;
import com.example.platform.commerce.app.PurchaseFulfillmentPort;
import com.example.platform.payment.app.*;
import com.example.platform.payment.domain.*;
import com.example.platform.shared.commercial.*;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"platform.payment.routing.default-provider-code=ep11-test","platform.payment.outbox.dispatch-delay-ms=3600000"})
@ActiveProfiles({"test","preview"})
class CommercePaymentPortIntegrationTest extends PostgresTestContainerSupport {
    @TestConfiguration static class ProviderConfig {
        @Bean PaymentProvider ep11Provider() {
            var provider=mock(PaymentProvider.class);
            when(provider.code()).thenReturn(new ProviderCode("ep11-test"));
            when(provider.createCheckout(any())).thenAnswer(call->{
                InitiateCheckoutCommand c=call.getArgument(0);
                return new CheckoutResult("ref-"+c.checkoutSessionId(),"https://test.invalid/checkout");
            });
            return provider;
        }
    }
    @Autowired CheckoutPaymentPort payment;
    @Autowired CheckoutOrderPort orders;
    @Autowired CheckoutOrchestrator checkout;
    @Autowired PaymentTransactionAuthority authority;
    @Autowired PaymentOutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @MockitoBean PurchaseFulfillmentPort fulfillment;
    @AfterEach void clear(){TenantContext.clear();}

    @Test void assembledPublicInitiationRetainsDurableIdempotencyAndRejectsChangedPayload() {
        String id="ep11-"+UUID.randomUUID(); Instant at=Instant.now();
        var request=new CheckoutPaymentPort.CheckoutPaymentRequest(id,"ep11","actor","pro_monthly",9999,"USD",
                "https://test.invalid/success",null,null,id,id,at);
        assertEquals(1,context.getBeansOfType(CheckoutPaymentPort.class).size());
        assertEquals(1,context.getBeansOfType(CheckoutOrderPort.class).size());
        var first=payment.createPaymentForCheckout(request);
        assertEquals(first,payment.createPaymentForCheckout(request));
        assertEquals(1,jdbc.queryForObject("select count(*) from payment_transaction where checkout_session_id=?",Integer.class,id));
        assertEquals("actor",jdbc.queryForObject("select principal_id from payment_transaction where checkout_session_id=?",String.class,id));
        assertThrows(IllegalStateException.class,()->payment.createPaymentForCheckout(new CheckoutPaymentPort.CheckoutPaymentRequest(
                id,"ep11","actor","pro_monthly",10000,"USD","https://test.invalid/success",null,null,id,id,at)));
        assertEquals(9999,jdbc.queryForObject("select amount_minor from payment_transaction where checkout_session_id=?",Long.class,id));
    }

    @Test void handoffCannotSelectAForeignCheckoutOrOverrideAnExistingTenantContext() {
        String tenant="ep11-"+UUID.randomUUID(); TenantContext.set(tenant);
        var session=checkout.createSession(new CreateCheckoutSessionRequest(tenant,"pro_monthly","owner","subscription","https://test.invalid/success",null));
        var consumer=context.getBean(PaymentSettlementProjectionPort.class);
        var wrong=new PaymentSettlementProjectionPort.PaymentSettledEvent("event","transaction","foreign","ep11-test","ref",session.checkoutSessionId(),"trace");
        assertThrows(RuntimeException.class,()->consumer.onPaymentSettled(wrong));
        assertEquals(tenant,TenantContext.get());
        TenantContext.clear();
        assertThrows(IllegalArgumentException.class,()->consumer.onPaymentSettled(wrong));
        assertNull(TenantContext.get());
        assertEquals(0,jdbc.queryForObject("select count(*) from purchase_order where checkout_session_id=?",Integer.class,session.checkoutSessionId()));
        assertEquals("owner",jdbc.queryForObject("select user_id from checkout_session where id=?",String.class,session.checkoutSessionId()));
        verifyNoInteractions(fulfillment);
    }

    @Test void durableSettlementRetriesThroughTheCommercePublicOrderPortWithoutPartialOrder() {
        String tenant="ep11-"+UUID.randomUUID();TenantContext.set(tenant);
        var session=checkout.createSession(new CreateCheckoutSessionRequest(tenant,"pro_monthly","persisted-actor","subscription","https://test.invalid/success",null));
        var row=jdbc.queryForMap("select * from payment_transaction where checkout_session_id=?",session.checkoutSessionId());
        Instant now=Instant.now();
        var command=new ApplyWebhookCommand(PrincipalRef.tenantScoped(tenant,PrincipalType.USER,"persisted-actor"),
                (String)row.get("id"),"ep11-test","evt-"+UUID.randomUUID(),(String)row.get("provider_reference"),"settled",1,
                PaymentState.SETTLED,"test-digest",((Number)row.get("version")).longValue(),"test","test","test",now,now,true);
        authority.applyWebhook(command);authority.applyWebhook(command);
        TenantContext.clear(); // scheduled dispatch has no HTTP/request tenant context
        doThrow(new IllegalStateException("controlled downstream rejection")).when(fulfillment).fulfill(any());
        assertThrows(IllegalStateException.class,dispatcher::dispatchNext);
        assertNull(TenantContext.get());
        assertEquals(0,jdbc.queryForObject("select count(*) from purchase_order where checkout_session_id=?",Integer.class,session.checkoutSessionId()));
        assertEquals(1,jdbc.queryForObject("select count(*) from payment_outbox where checkout_session_id=? and dispatched_at is null",Integer.class,session.checkoutSessionId()));
        assertEquals("PENDING",jdbc.queryForObject("select session_status from checkout_session where id=?",String.class,session.checkoutSessionId()));
        doNothing().when(fulfillment).fulfill(any());
        assertTrue(dispatcher.dispatchNext()); assertFalse(dispatcher.dispatchNext());
        assertNull(TenantContext.get());
        assertEquals(1,jdbc.queryForObject("select count(*) from purchase_order where checkout_session_id=?",Integer.class,session.checkoutSessionId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from payment_outbox where checkout_session_id=? and dispatched_at is null",Integer.class,session.checkoutSessionId()));
        assertEquals("COMPLETED",jdbc.queryForObject("select session_status from checkout_session where id=?",String.class,session.checkoutSessionId()));
        verify(fulfillment,atLeastOnce()).fulfill(argThat(c->"persisted-actor".equals(c.userId())&&tenant.equals(c.tenantId())));
    }
}
