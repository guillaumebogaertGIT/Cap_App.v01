package be.cap.backend.accounts;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static be.cap.backend.accounts.StaffPolicy.Permission.NUTRITION_MANAGE;

class FoodProductAuthorizationTests {
    @Test void productsAreReadOnlyAndRespectNutritionPermission() {
        var policy=mock(StaffPolicy.class);
        var requests=new StaffRequestAuthorization(policy);
        var athlete=new AccountService.Account(UUID.randomUUID(),"athlete@example.test","Athlete","ATHLETE");
        var coach=new AccountService.Account(UUID.randomUUID(),"coach@example.test","Coach","COACH");
        String path="/api/food-products/3017620422003";
        assertTrue(requests.allowed(athlete,"GET",path));
        assertFalse(requests.allowed(athlete,"POST",path));
        assertFalse(requests.allowed(coach,"GET",path));
        when(policy.allows(coach,NUTRITION_MANAGE)).thenReturn(true);
        assertTrue(requests.allowed(coach,"GET",path));
        assertFalse(requests.allowed(coach,"PUT",path));
    }
}
