package be.cap.backend.accounts;

import be.cap.backend.FoodProductService;
import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("accounts")
@Transactional
public class PersonalFoodProductService {
    public record Input(String name,String brand,String unit,Double baseAmount,Double calories,Double proteinG,Double carbsG,Double fatG) {}
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public PersonalFoodProductService(JdbcTemplate jdbc,AccountService accounts){this.jdbc=jdbc;this.accounts=accounts;}
    private AccountService.Account owner(Principal principal) {
        if(principal==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var account=accounts.account(principal.getName());
        if(!account.role().equals("ATHLETE"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return account;
    }
    private String key(String raw) {
        String code=FoodProductService.validBarcode(raw);
        if(code.length()==12)return "0"+code;
        if(code.length()==14&&code.startsWith("0"))return code.substring(1);
        return code;
    }
    private String text(String raw,int max,boolean required) {
        String value=raw==null?"":raw.trim();
        if(value.length()>max || required&&value.isBlank())throw invalid();
        return value;
    }
    private Double number(Double value,double factor,double max) {
        if(value==null)return null;
        double normalized=value*factor;
        if(!Double.isFinite(value)||value<0||value>1000000||!Double.isFinite(normalized)||normalized>max)throw invalid();
        return normalized;
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer naam, eenheid, etiketportie en voedingswaarden. Laat onbekende waarden leeg.");}
    @Transactional(readOnly=true)
    public List<FoodProductService.Product> list(Principal principal) {
        var user=owner(principal);
        return jdbc.query("SELECT payload FROM personal_food_product WHERE user_id=? ORDER BY updated_at DESC",
            (row,index)->mapper.readValue(row.getString(1),FoodProductService.Product.class),user.id());
    }
    @Transactional(readOnly=true)
    public FoodProductService.Product find(Principal principal,String raw) {
        if(principal==null)return null;
        var user=accounts.account(principal.getName());
        if(!user.role().equals("ATHLETE"))return null;
        String barcode=FoodProductService.validBarcode(raw);
        var values=jdbc.query("SELECT payload FROM personal_food_product WHERE user_id=? AND barcode=?",
            (row,index)->mapper.readValue(row.getString(1),FoodProductService.Product.class),user.id(),key(barcode));
        if(values.isEmpty())return null;
        var value=values.getFirst();
        return new FoodProductService.Product(barcode,value.name(),value.brand(),value.unit(),value.calories(),value.proteinG(),value.carbsG(),value.fatG(),value.complete(),null,"personal");
    }
    public FoodProductService.Product save(Principal principal,String raw,Input input) {
        var user=owner(principal);String barcode=key(raw);
        if(input==null)throw invalid();
        String name=text(input.name(),120,true),brand=text(input.brand(),160,false);
        if(!List.of("g","ml").contains(input.unit()==null?"":input.unit()))throw invalid();
        if(input.baseAmount()==null||!Double.isFinite(input.baseAmount())||input.baseAmount()<=0||input.baseAmount()>100000)throw invalid();
        double factor=100/input.baseAmount();
        Double calories=number(input.calories(),factor,1500),protein=number(input.proteinG(),factor,100),carbs=number(input.carbsG(),factor,100),fat=number(input.fatG(),factor,100);
        boolean complete=calories!=null&&protein!=null&&carbs!=null&&fat!=null;
        if(protein!=null&&carbs!=null&&fat!=null&&protein+carbs+fat>110)throw invalid();
        var product=new FoodProductService.Product(barcode,name,brand,input.unit(),calories,protein,carbs,fat,complete,null,"personal");
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,user.id());
        boolean exists=jdbc.queryForObject("SELECT COUNT(*) FROM personal_food_product WHERE user_id=? AND barcode=?",Integer.class,user.id(),barcode)>0;
        if(exists)jdbc.update("UPDATE personal_food_product SET payload=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND barcode=?",mapper.writeValueAsString(product),user.id(),barcode);
        else {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM personal_food_product WHERE user_id=?",Integer.class,user.id())>=500)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Je hebt 500 eigen producten. Verwijder eerst een product dat je niet meer gebruikt.");
            jdbc.update("INSERT INTO personal_food_product(user_id,barcode,payload) VALUES(?,?,?)",user.id(),barcode,mapper.writeValueAsString(product));
        }
        return product;
    }
    public void delete(Principal principal,String raw) {
        var user=owner(principal);
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,user.id());
        if(jdbc.update("DELETE FROM personal_food_product WHERE user_id=? AND barcode=?",user.id(),key(raw))==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Eigen product niet gevonden.");
    }
}
