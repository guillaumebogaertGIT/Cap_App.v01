package be.cap.backend.accounts;

import java.util.List;

public interface AiCoachGateway {
    record Turn(String role,String content) {}
    boolean configured();
    String answer(List<Turn> turns) throws Exception;
}
