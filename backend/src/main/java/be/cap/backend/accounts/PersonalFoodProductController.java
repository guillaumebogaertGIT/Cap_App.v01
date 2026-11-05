package be.cap.backend.accounts;

import be.cap.backend.FoodProductService;
import java.security.Principal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("accounts")
@RequestMapping("/api/food-products/personal")
class PersonalFoodProductController {
    private final PersonalFoodProductService products;
    PersonalFoodProductController(PersonalFoodProductService products){this.products=products;}
    @GetMapping ResponseEntity<List<FoodProductService.Product>> list(Principal principal){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.list(principal));}
    @PutMapping("/{barcode}") ResponseEntity<FoodProductService.Product> save(Principal principal,@PathVariable String barcode,@RequestBody PersonalFoodProductService.Input input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.save(principal,barcode,input));}
    @DeleteMapping("/{barcode}") ResponseEntity<Void> delete(Principal principal,@PathVariable String barcode){products.delete(principal,barcode);return ResponseEntity.noContent().build();}
}
