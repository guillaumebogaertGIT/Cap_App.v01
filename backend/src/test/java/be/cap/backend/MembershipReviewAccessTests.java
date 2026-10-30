package be.cap.backend;

import be.cap.backend.accounts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipReviewAccessTests {
    @Test void grantsUseCurrentAccountIdRoleNotLoginNameOrOldAllowlist(){
        var policy=spy(new StaffPolicy(mock(JdbcTemplate.class)));
        var user=new AccountService.Account(UUID.randomUUID(),"owner@example.test","Glenn","COACH");
        var access=new MembershipReviewAccess(policy);
        for(String allowed:List.of("OWNER","MANAGER","FINANCE")){
            doReturn(allowed).when(policy).role(user);assertTrue(access.allows(user));
        }
        for(String denied:List.of("INSTRUCTOR","CLIENT","UNASSIGNED","DEVELOPMENT")){
            doReturn(denied).when(policy).role(user);assertFalse(access.allows(user));
        }
    }
}