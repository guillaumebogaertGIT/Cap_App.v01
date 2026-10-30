package be.cap.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreviewSafetyTests {
    @Test void publicBindingAndProductionCannotStartUnauthenticatedPreview(){
        var environment=mock(Environment.class);
        assertThrows(IllegalStateException.class,()->new PreviewSecurity().previewFilterChain(null,"0.0.0.0",environment));
        when(environment.matchesProfiles("prod","production")).thenReturn(true);
        assertThrows(IllegalStateException.class,()->new PreviewSecurity().previewFilterChain(null,"127.0.0.1",environment));
    }
}
