package com.example.platform.commerce.app;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.commerce.domain.*;
import com.example.platform.shared.commercial.Money;
import java.time.Instant;
import java.util.List;

final class CatalogTestFixtures {
    static CommerceCatalogService catalog() {
        CommerceCatalogService service = mock(CommerceCatalogService.class);
        when(service.requireOffering(any(), anyString(), anyString(), any())).thenAnswer(i -> offering(i.getArgument(2)));
        when(service.requireHistorical(any(), anyString(), anyLong())).thenAnswer(i -> offeringById(i.getArgument(1)));
        when(service.requireProduct(any(), anyString(), anyString())).thenAnswer(i -> CommerceCatalogService.project(offering(i.getArgument(2))));
        when(service.findProduct(any(), anyString(), anyString())).thenAnswer(i -> java.util.Optional.of(CommerceCatalogService.project(offering(i.getArgument(2)))));
        when(service.listProducts(any(), anyString())).thenReturn(codes().stream().map(CatalogTestFixtures::offering).map(CommerceCatalogService::project).toList());
        return service;
    }
    static List<String> codes() { return List.of("basic_monthly","pro_monthly","team_monthly","enterprise_monthly","addon_gpu_monthly","addon_ai_monthly","credit_pack_50","credit_pack_200","seat_pack_5"); }
    static CommercialOffering offeringById(String id) { return offering(id.replace("offer-", "")); }
    static CommercialOffering offering(String code) {
        PurchaseMode mode = code.startsWith("credit") ? PurchaseMode.CREDIT_PACK : code.startsWith("seat") ? PurchaseMode.SEAT_PACK : PurchaseMode.SUBSCRIPTION;
        long amount = code.equals("pro_monthly") ? 9999 : code.equals("basic_monthly") ? 2999 : code.equals("credit_pack_50") ? 5000 : 1999;
        ProductLineType line = mode == PurchaseMode.CREDIT_PACK ? ProductLineType.CREDIT_PACK
                : mode == PurchaseMode.SEAT_PACK ? ProductLineType.SEAT_PACK
                : code.startsWith("addon_") ? ProductLineType.ADD_ON_SUBSCRIPTION : ProductLineType.BASE_SUBSCRIPTION;
        return new CommercialOffering("offer-"+code,"product-"+code,code,line,code,code,1,OfferingLifecycleState.ACTIVE,2,mode,
                "GLOBAL","GLOBAL",Instant.EPOCH,null, mode==PurchaseMode.SUBSCRIPTION?new AuthorityReference("bundle-"+code,1):null,
                null,mode==PurchaseMode.SUBSCRIPTION?new AuthorityReference(code,1):null,new AuthorityReference("price-"+code,1),
                new Money(amount,"USD"),mode==PurchaseMode.CREDIT_PACK?amount:null,mode==PurchaseMode.SEAT_PACK?5:null,
                mode==PurchaseMode.SEAT_PACK?"render.minutes":null,Instant.EPOCH,Instant.EPOCH);
    }
    private CatalogTestFixtures() {}
}
