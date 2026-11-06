package be.cap.backend;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/food-products")
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class FoodProductController {
    private final FoodProductService products;
    private final FoodProductSearchService searches;
    private final org.springframework.beans.factory.ObjectProvider<be.cap.backend.accounts.PersonalFoodProductService> personal;
    private final org.springframework.beans.factory.ObjectProvider<be.cap.backend.accounts.CommunityFoodProductService> community;
    public FoodProductController(FoodProductService products, FoodProductSearchService searches,org.springframework.beans.factory.ObjectProvider<be.cap.backend.accounts.PersonalFoodProductService> personal,org.springframework.beans.factory.ObjectProvider<be.cap.backend.accounts.CommunityFoodProductService> community) { this.products = products; this.searches = searches; this.personal=personal;this.community=community; }
    @GetMapping("/search")
    public ResponseEntity<FoodProductSearchService.Results> search(@RequestParam String query, @RequestParam(defaultValue="1") int page,java.security.Principal principal) {
        FoodProductSearchService.query(query);
        if(page<1||page>20)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        var shared=community.getIfAvailable();
        var local=shared==null||page!=1?java.util.List.<FoodProductSearchService.Hit>of():shared.search(principal,query);
        FoodProductSearchService.Results remote;
        try{remote=searches.search(query,page);}catch(org.springframework.web.server.ResponseStatusException error){if(local.isEmpty())throw error;remote=new FoodProductSearchService.Results(java.util.List.of(),page,false);}
        var combined=new java.util.LinkedHashMap<String,FoodProductSearchService.Hit>();
        for(var hit:local)combined.put(hit.barcode(),hit);
        for(var hit:remote.products())combined.putIfAbsent(hit.barcode(),hit);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new FoodProductSearchService.Results(java.util.List.copyOf(combined.values()),page,remote.hasMore()));
    }
    @GetMapping("/{barcode}")
    public ResponseEntity<FoodProductService.Product> lookup(@PathVariable String barcode,java.security.Principal principal) {
        var own=personal.getIfAvailable();var saved=own==null?null:own.find(principal,barcode);
        var shared=community.getIfAvailable();if(saved==null&&shared!=null)saved=shared.find(principal,barcode);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(saved==null?products.lookup(barcode):saved);
    }
}
