package com.example.platform.web.assets;
import com.example.platform.marketplace.api.MarketplaceApi;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MarketplaceControllerTest {
    @Test void publicStatusOverrideCannotSelectPrivateRows() {
        var api=mock(MarketplaceApi.class);var controller=new MarketplaceController(api);
        assertThatThrownBy(()->controller.search(null,null,"DRAFT",null,null,0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->controller.search(null,null,null,"foreign",null,0,20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(api);
    }
    @Test void forgedActorAndUnsupportedSubjectHaveNoOwnerCommand() {
        var api=mock(MarketplaceApi.class);var controller=new MarketplaceController(api);
        assertThatThrownBy(()->controller.create("p","{\"commandId\":\"c\",\"actorId\":\"forged\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->controller.create("p","{\"commandId\":\"c\",\"subject\":{\"kind\":\"PLUGIN\"}}")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(api);
    }
}
