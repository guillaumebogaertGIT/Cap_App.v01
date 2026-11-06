package be.cap.backend.accounts;

import be.cap.backend.FoodProductService;
import be.cap.backend.FoodProductSearchService;
import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Shared label submissions, separate from personal products and public provider data. */
@Service @Profile("accounts") @Transactional
public class CommunityFoodProductService {
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final JsonMapper mapper=JsonMapper.builder().build();
    public CommunityFoodProductService(JdbcTemplate jdbc,AccountService accounts){this.jdbc=jdbc;this.accounts=accounts;}
    private AccountService.Account user(Principal p){if(p==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return accounts.account(p.getName());}
    @Transactional(readOnly=true)
    public FoodProductService.Product find(Principal p,String raw){
        user(p);String code=FoodProductService.validBarcode(raw);
        var rows=jdbc.query("SELECT payload FROM community_food_product WHERE barcode=?",(r,i)->mapper.readValue(r.getString(1),FoodProductService.Product.class),PersonalFoodProductService.key(code));
        if(rows.isEmpty())return null;
        var v=rows.getFirst();return new FoodProductService.Product(code,v.name(),v.brand(),v.unit(),v.calories(),v.proteinG(),v.carbsG(),v.fatG(),v.complete(),null,"community");
    }
    @Transactional(readOnly=true)
    public List<FoodProductSearchService.Hit> search(Principal p,String raw){
        user(p);String query=FoodProductSearchService.query(raw);
        // LIKE wildcards cannot be supplied by the user; AND across words, name + brand.
        var sql=new StringBuilder("SELECT barcode,payload FROM community_food_product WHERE TRUE");var args=new ArrayList<Object>();
        for(String word:query.split(" ")){sql.append(" AND LOWER(CAST(payload AS jsonb)->>'name' || ' ' || COALESCE(CAST(payload AS jsonb)->>'brand','')) LIKE ?");args.add("%"+word+"%");}
        sql.append(" ORDER BY created_at DESC,barcode LIMIT 12");
        return jdbc.query(sql.toString(),(r,i)->{var v=mapper.readValue(r.getString("payload"),FoodProductService.Product.class);return new FoodProductSearchService.Hit(r.getString("barcode"),v.name(),v.brand(),"community");},args.toArray());
    }
    public FoodProductService.Product save(Principal p,String raw,PersonalFoodProductService.Input input){
        var user=user(p);String barcode=PersonalFoodProductService.key(raw);
        var product=PersonalFoodProductService.normalize(barcode,input,"community");
        if(!product.complete())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vul alle voedingswaarden van het etiket in om met CAP te delen. Onbekende waarden? Bewaar het product alleen voor jezelf.");
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,user.id());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM community_food_product WHERE contributor_id=?",Integer.class,user.id())>=500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Je hebt de limiet van 500 gedeelde producten bereikt.");
        if(jdbc.update("INSERT INTO community_food_product(barcode,contributor_id,payload) VALUES(?,?,?) ON CONFLICT(barcode) DO NOTHING",barcode,user.id(),mapper.writeValueAsString(product))==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze barcode staat al in de CAP-productlijst. Zoek de barcode opnieuw op, of bewaar jouw versie alleen voor jezelf.");
        return product;
    }
}
