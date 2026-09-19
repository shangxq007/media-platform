package com.example.platform.web.assets;

import com.example.platform.marketplace.api.MarketplaceApi;
import com.example.platform.marketplace.api.MarketplaceApi.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Existing asset-facing callers now use the same persisted Marketplace owner commands. */
@RestController
@RequestMapping("/api/projects/{projectId}/assets/{assetId}")
public class AssetPublishController {
    private final MarketplaceApi marketplace;
    public AssetPublishController(MarketplaceApi marketplace){this.marketplace=marketplace;}
    private Listing listing(String project,String asset){var row=marketplace.managedByAsset(asset).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Create a Marketplace listing first"));return marketplace.managedListing(project,row.id());}
    @PostMapping("/submit-review") @ResponseStatus(HttpStatus.CREATED)
    public Review submit(@PathVariable String projectId,@PathVariable String assetId,@RequestBody String json){return marketplace.submit(projectId,listing(projectId,assetId).id(),MarketplaceController.body(json,Submit.class));}
    @GetMapping("/review") public ResponseEntity<Review> get(@PathVariable String projectId,@PathVariable String assetId){var l=listing(projectId,assetId);return l.reviewId()==null?ResponseEntity.notFound().build():ResponseEntity.ok(marketplace.review(projectId,l.reviewId()));}
    @PostMapping({"/approve-review","/reject-review"})
    public Review decide(@PathVariable String projectId,@PathVariable String assetId,@RequestBody String json,jakarta.servlet.http.HttpServletRequest request){
        var c=MarketplaceController.body(json,Decide.class);var expected=request.getRequestURI().endsWith("/approve-review")?Decision.APPROVE:Decision.REJECT;
        if(c.decision()!=expected||request.getParameter("reviewerUserId")!=null)throw new IllegalArgumentException("Decision or actor override does not match command");
        var l=listing(projectId,assetId);if(l.reviewId()==null)throw new IllegalArgumentException("No review");return marketplace.decide(projectId,l.reviewId(),c);
    }
    @PostMapping({"/publish","/archive"})
    public Listing transition(@PathVariable String projectId,@PathVariable String assetId,@RequestBody String json,jakarta.servlet.http.HttpServletRequest request){
        var c=MarketplaceController.body(json,Change.class);var expected=request.getRequestURI().endsWith("/publish")?Transition.PUBLISH:Transition.ARCHIVE;
        if(c.transition()!=expected)throw new IllegalArgumentException("Transition does not match command");return marketplace.transition(projectId,listing(projectId,assetId).id(),c);
    }
    @GetMapping("/publish-status") public Map<String,Object> status(@PathVariable String projectId,@PathVariable String assetId){var l=listing(projectId,assetId);return Map.of("assetId",assetId,"listingId",l.id(),"publishStatus",l.status(),"version",l.version(),"canPublish",marketplace.canPublish(projectId,l.id()));}
    @GetMapping("/review-summary") public Map<String,Object> summary(@PathVariable String projectId,@PathVariable String assetId){var l=listing(projectId,assetId);return l.reviewId()==null?Map.of("assetId",assetId,"hasReview",false):Map.of("assetId",assetId,"hasReview",true,"reviewId",l.reviewId(),"status",marketplace.review(projectId,l.reviewId()).status());}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalid(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage());}
}
