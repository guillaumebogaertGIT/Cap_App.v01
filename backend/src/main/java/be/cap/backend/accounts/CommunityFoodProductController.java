package be.cap.backend.accounts;

import be.cap.backend.FoodProductService;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("accounts")
@RequestMapping("/api/food-products/community")
class CommunityFoodProductController {
    private final CommunityFoodProductService products;
    CommunityFoodProductController(CommunityFoodProductService products){this.products=products;}
    @PutMapping("/{barcode}") ResponseEntity<FoodProductService.Product> save(Principal p,@PathVariable String barcode,@RequestBody PersonalFoodProductService.Input input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.save(p,barcode,input));}
}
