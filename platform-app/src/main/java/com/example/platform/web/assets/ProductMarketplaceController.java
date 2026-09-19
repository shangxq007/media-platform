package com.example.platform.web.assets;
import com.example.platform.marketplace.api.MarketplaceApi;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** Productization transport delegates to the single Marketplace authority. No in-memory publication. */
@RestController
@RequestMapping("/api/product/marketplace/{marketplaceId}")
public class ProductMarketplaceController {
    private final MarketplaceApi marketplace;
    public ProductMarketplaceController(MarketplaceApi marketplace){this.marketplace=marketplace;}
    public record CreateItem(String projectId,MarketplaceApi.Create listing) {}
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public MarketplaceApi.Listing create(@PathVariable String marketplaceId,@RequestBody String json){var c=MarketplaceController.body(json,CreateItem.class);return marketplace.create(c.projectId(),marketplaceId,c.listing());}
    @GetMapping("/search") public MarketplaceApi.SearchResult search(@PathVariable String marketplaceId,@RequestParam String query,@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){return marketplace.discover(query,marketplaceId,offset,limit);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalid(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage());}
}
