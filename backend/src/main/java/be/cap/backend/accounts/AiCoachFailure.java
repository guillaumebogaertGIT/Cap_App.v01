package be.cap.backend.accounts;

/** Fixed diagnostic codes only: never include provider bodies, keys or chat text. */
public class AiCoachFailure extends Exception {
    private final String code;
    public AiCoachFailure(String code){super(code);this.code=code;}
    public String code(){return code;}
    public String userMessage(){return switch(code){
        case "provider_quota" -> "De AI-dienst meldt dat het API-tegoed of de bestedingslimiet is bereikt.";
        case "provider_key" -> "De AI-dienst accepteert de ingestelde API-sleutel niet.";
        case "provider_rate" -> "De AI-dienst is tijdelijk te druk. Probeer later opnieuw.";
        case "provider_request" -> "De AI-dienst accepteert de aanvraag niet. De AI-koppeling moet worden nagekeken.";
        case "provider_incomplete" -> "Het AI-antwoord werd niet volledig afgerond.";
        default -> "De AI-dienst kon geen antwoord geven.";
    };}
}
