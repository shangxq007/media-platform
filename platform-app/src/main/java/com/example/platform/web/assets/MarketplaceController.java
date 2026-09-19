package com.example.platform.web.assets;

import com.example.platform.marketplace.api.MarketplaceApi;
import com.example.platform.marketplace.api.MarketplaceApi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adaptation only; all state, authorization and events belong to Marketplace. */
@RestController
@RequestMapping("/api")
public class MarketplaceController {
    private final MarketplaceApi marketplace;
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public MarketplaceController(MarketplaceApi marketplace){this.marketplace=marketplace;}
    static <T> T body(String json,Class<T> type){try{T value=JSON.readValue(json,type);if(value==null)throw new IllegalArgumentException("Request required");return value;}catch(Exception e){throw new IllegalArgumentException("Invalid Marketplace request",e);}}
    private void publishedOnly(String status,String listingType){if(status!=null&&!status.equals("PUBLISHED")||listingType!=null&&!listingType.equals("MEDIA"))throw new IllegalArgumentException("Public discovery supports PUBLISHED Media listings only");}
    @GetMapping("/marketplace/search")
    public SearchResult search(@RequestParam(required=false) String q,@RequestParam(required=false) String listingType,
            @RequestParam(required=false) String status,@RequestParam(required=false) String tenantId,
            @RequestParam(required=false) String projectId,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit) {
        publishedOnly(status,listingType);
        if(tenantId!=null||projectId!=null)throw new IllegalArgumentException("Private scope filters use the authorized project management API");
        return marketplace.discover(q,null,offset,limit);
    }
    @GetMapping("/marketplace/listings") public List<PublicListing> list(@RequestParam(required=false) String status,@RequestParam(defaultValue="20") int limit){publishedOnly(status,null);return marketplace.discover(null,null,0,limit).results();}
    @GetMapping("/marketplace/listings/{id}") public ResponseEntity<PublicListing> get(@PathVariable String id){return ResponseEntity.of(marketplace.publicListing(id));}
    @GetMapping("/marketplace/assets/{assetId}/listing") public ResponseEntity<Listing> byAsset(@PathVariable String assetId,@RequestParam(required=false) String tenantId){
        if(tenantId!=null)throw new IllegalArgumentException("Actor scope is server-resolved");return ResponseEntity.of(marketplace.managedByAsset(assetId));}
    @GetMapping("/marketplace/discovery") public Map<String,List<PublicListing>> discovery(@RequestParam(defaultValue="10") int limit){return Map.of("recent",marketplace.discover(null,null,0,limit).results(),"popular",List.of(),"featured",List.of());}
    @GetMapping("/projects/{project}/marketplace/listings") public List<Listing> managed(@PathVariable String project,@RequestParam(defaultValue="20") int limit){return marketplace.managedByProject(project,limit);}
    @GetMapping("/projects/{project}/marketplace/listings/{id}") public Listing managed(@PathVariable String project,@PathVariable String id){return marketplace.managedListing(project,id);}
    @PostMapping("/projects/{project}/marketplace/listings") @ResponseStatus(HttpStatus.CREATED)
    public Listing create(@PathVariable String project,@RequestBody String json){return marketplace.create(project,null,body(json,Create.class));}
    @PatchMapping("/projects/{project}/marketplace/listings/{id}") public Listing edit(@PathVariable String project,@PathVariable String id,@RequestBody String json){return marketplace.edit(project,id,body(json,Edit.class));}
    @PostMapping("/projects/{project}/marketplace/listings/{id}/reviews") @ResponseStatus(HttpStatus.CREATED)
    public Review submit(@PathVariable String project,@PathVariable String id,@RequestBody String json){return marketplace.submit(project,id,body(json,Submit.class));}
    @PostMapping("/projects/{project}/marketplace/listings/{id}/transitions") public Listing transition(@PathVariable String project,@PathVariable String id,@RequestBody String json){return marketplace.transition(project,id,body(json,Change.class));}
    @GetMapping("/projects/{project}/marketplace/reviews/{id}") public Review review(@PathVariable String project,@PathVariable String id){return marketplace.review(project,id);}
    @PostMapping("/projects/{project}/marketplace/reviews/{id}/decisions") public Review decide(@PathVariable String project,@PathVariable String id,@RequestBody String json){return marketplace.decide(project,id,body(json,Decide.class));}
    @PostMapping("/projects/{project}/marketplace/reviews/{id}/comments") public Review comment(@PathVariable String project,@PathVariable String id,@RequestBody String json){return marketplace.comment(project,id,body(json,Comment.class));}
    @PostMapping("/projects/{project}/marketplace/reviews/{id}/resolve") public Review resolve(@PathVariable String project,@PathVariable String id,@RequestBody String json){return marketplace.resolve(project,id,body(json,Resolve.class));}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalid(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage());}
}
