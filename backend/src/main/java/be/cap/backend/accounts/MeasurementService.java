package be.cap.backend.accounts;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Profile("accounts")
public class MeasurementService {
    public record Entry(UUID id,Instant recordedAt,String source,Map<String,Double> values) {}
    public record Snapshot(Map<String,Double> profile,List<Entry> measurements) {}
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    private final Map<String,Double> limits=new HashMap<>();
    public MeasurementService(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        for(String key:List.of("weightKg","muscleKg","fatKg","fatFreeKg","waterKg"))limits.put(key,500d);
        for(String key:List.of("bodyFatPercent","waterPercent","musclePercent","visceralFat","boneKg"))limits.put(key,100d);
        limits.put("heightCm",300d);limits.put("bmr",10000d);limits.put("metabolicAge",150d);limits.put("bmi",200d);
        for(String part:List.of("rightArm","leftArm","trunk","rightLeg","leftLeg")) {
            limits.put(part+"FatPercent",100d);
            for(String suffix:List.of("FatKg","FatFreeKg","MuscleKg"))limits.put(part+suffix,500d);
        }
    }
    public Map<String,Double> profileMetrics(Double height,Double weight) {
        var result=new LinkedHashMap<String,Double>();if(height!=null)result.put("heightCm",height);if(weight!=null)result.put("weightKg",weight);return result;
    }
    public List<Entry> history(UUID owner,Map<String,Double> profile) {
        var entries=jdbc.query("SELECT id,recorded_at,source,values_json FROM account_measurement WHERE user_id=? ORDER BY recorded_at DESC,id DESC LIMIT 200",
            (row,index)->new Entry(row.getObject("id",UUID.class),row.getTimestamp("recorded_at").toInstant(),row.getString("source"),decode(row.getString("values_json"))),owner);
        // Existing profile values remain visible, without inventing their original measurement date.
        if(entries.isEmpty() && !profile.isEmpty())entries.add(new Entry(null,null,"profile",profile));
        return entries;
    }
    private Map<String,Double> decode(String value) {
        var result=new LinkedHashMap<String,Double>();var tree=json.readTree(value);
        for(String key:limits.keySet())if(tree.path(key).isNumber())result.put(key,tree.path(key).asDouble());
        return result;
    }
    public Snapshot snapshot(UUID owner) {
        var profiles=jdbc.query("SELECT values_json FROM account_profile WHERE user_id=?",(row,index)->decode(row.getString(1)),owner);
        var current=profiles.isEmpty()?Map.<String,Double>of():profileMetrics(profiles.getFirst().get("heightCm"),profiles.getFirst().get("weightKg"));
        return new Snapshot(current,history(owner,current));
    }
    public void recordProfile(UUID owner,Map<String,Double> values) { if(!values.isEmpty())append(owner,values,"profile"); }
    private void append(UUID owner,Map<String,Double> values,String source) {
        jdbc.update("INSERT INTO account_measurement(id,user_id,recorded_at,source,values_json) VALUES(?,?,?,?,?)",
            UUID.randomUUID(),owner,Timestamp.from(Instant.now()),source,json.writeValueAsString(values));
    }
    @Transactional
    public void save(AccountService.Account owner,Map<String,Double> input) {
        if(input==null || input.isEmpty() || input.size()>limits.size())throw invalid();
        var values=new LinkedHashMap<String,Double>();
        input.forEach((key,value)->{
            double min=key.equals("heightCm")?50:key.equals("weightKg")?20:0;
            if(!limits.containsKey(key) || value==null || !Double.isFinite(value) || value<min || value>limits.get(key))throw invalid();
            values.put(key,value);
        });
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,owner.id());
        if(values.containsKey("heightCm") || values.containsKey("weightKg")) {
            var profiles=jdbc.query("SELECT values_json FROM account_profile WHERE user_id=?",(row,index)->row.getString(1),owner.id());
            ObjectNode profile=profiles.isEmpty()?json.valueToTree(new ProfileController.Values(owner.name(),"","",null,null,"","","")):(ObjectNode)json.readTree(profiles.getFirst());
            for(String key:List.of("heightCm","weightKg"))if(values.containsKey(key))profile.put(key,values.get(key));
            if(profiles.isEmpty())jdbc.update("INSERT INTO account_profile(user_id,values_json) VALUES(?,?)",owner.id(),json.writeValueAsString(profile));
            else jdbc.update("UPDATE account_profile SET values_json=? WHERE user_id=?",json.writeValueAsString(profile),owner.id());
        }
        append(owner.id(),values,"manual");
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vul minstens één geldige meetwaarde in met de aangegeven eenheid.");}
}
