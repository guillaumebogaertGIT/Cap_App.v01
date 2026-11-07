package be.cap.backend.accounts;

import java.util.List;

public interface AiCoachGateway {
    record Turn(String role,String content) {}
    boolean configured();
    String answer(List<Turn> turns) throws Exception;
    default String answer(List<Turn> turns,String context) throws Exception {return answer(turns);}
    default String draft(List<Turn> turns,List<String> exercises,String context) throws Exception {return draft(turns,exercises);}
    default String draft(List<Turn> turns,List<String> exercises) throws Exception {throw new UnsupportedOperationException();}
}
