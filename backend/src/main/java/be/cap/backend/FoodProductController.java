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
    public FoodProductController(FoodProductService products, FoodProductSearchService searches,org.springframework.beans.factory.ObjectProvider<be.cap.backend.accounts.PersonalFoodProductService> personal) { this.products = products; this.searches = searches; this.personal=personal; }
    @GetMapping("/search")
    public ResponseEntity<FoodProductSearchService.Results> search(@RequestParam String query, @RequestParam(defaultValue="1") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(searches.search(query,page));
    }
    @GetMapping("/{barcode}")
    public ResponseEntity<FoodProductService.Product> lookup(@PathVariable String barcode,java.security.Principal principal) {
        var own=personal.getIfAvailable();var saved=own==null?null:own.find(principal,barcode);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(saved==null?products.lookup(barcode):saved);
    }
}
