package be.cap.backend.accounts.billing;

import be.cap.backend.accounts.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Private archive of existing invoices. No issuance, transmission or payment side effects. */
@RestController @Profile("accounts") @RequestMapping("/api/invoices")
public class InvoiceArchiveController {
    public record Input(UUID id,UUID clientId,String customer,String reference,LocalDate date,String description,String type,Long grossCents,Long netCents,String status,String filename,String data){}
    public record Status(String status,Integer version){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;
    public InvoiceArchiveController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;}
    private AccountService.Account reader(Principal p){var user=accounts.account(p.getName());policy.require(user,StaffPolicy.Permission.FINANCE_VIEW);return user;}
    private AccountService.Account owner(Principal p){var user=reader(p);if(!policy.role(user).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return user;}
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer factuurgegevens en PDF (maximaal 10 MB).");}
    @GetMapping public ResponseEntity<?> list(Principal p,@RequestParam(required=false) String month){
        reader(p);YearMonth selected;try{selected=month==null?YearMonth.now(ZoneId.of("Europe/Brussels")):YearMonth.parse(month);if(selected.getYear()<2000||selected.getYear()>2100)throw new IllegalArgumentException();}catch(RuntimeException error){throw invalid();}
        var rows=jdbc.queryForList("SELECT id,client_id,customer_name,reference,issued_on,description,document_type,gross_cents,net_cents,payment_status,filename,size_bytes,version FROM invoice_archive WHERE issued_on>=? AND issued_on<? ORDER BY issued_on DESC,reference,id LIMIT 501",selected.atDay(1),selected.plusMonths(1).atDay(1));
        if(rows.size()>500)throw new ResponseStatusException(HttpStatus.CONFLICT,"Meer dan 500 facturen in deze maand. Het overzicht wordt niet onvolledig getoond.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
    }
    @PostMapping @Transactional @ResponseStatus(HttpStatus.CREATED)
    public Map<String,UUID> upload(Principal p,@RequestBody Input input){
        var user=owner(p);
        if(input.id()==null||input.customer()==null||input.customer().isBlank()||input.customer().trim().length()>160||input.reference()==null||input.reference().isBlank()||input.reference().trim().length()>80||input.date()==null||input.date().getYear()<2000||input.date().getYear()>2100||input.description()==null||input.description().isBlank()||input.description().trim().length()>500||input.type()==null||!Set.of("invoice","credit_note").contains(input.type())||input.status()==null||!Set.of("open","paid").contains(input.status())||input.grossCents()==null||input.grossCents() < -10000000||input.grossCents()>10000000||input.type().equals("invoice")&&input.grossCents()<0||input.type().equals("credit_note")&&input.grossCents()>0)throw invalid();
        if(input.netCents()!=null&&(input.netCents() < -10000000||input.netCents()>10000000||Math.abs(input.netCents())>Math.abs(input.grossCents())||input.netCents()<0&&input.grossCents()>=0||input.netCents()>0&&input.grossCents()<=0))throw invalid();
        if(input.filename()==null||input.filename().length()>180||!input.filename().matches("[^\\\\/\\p{Cntrl}]+\\.(?i:pdf)")||input.data()==null||input.data().length()>13981016)throw invalid();
        byte[] bytes;try{bytes=Base64.getDecoder().decode(input.data());}catch(IllegalArgumentException error){throw invalid();}
        if(bytes.length<5||bytes.length>10485760||!new String(bytes,0,5,java.nio.charset.StandardCharsets.ISO_8859_1).equals("%PDF-"))throw invalid();
        if(input.clientId()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE'",Integer.class,input.clientId())!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.query("SELECT id FROM invoice_archive_lock WHERE id=1 FOR UPDATE",(r,i)->r.getInt(1));owner(p);
        var existing=jdbc.queryForList("SELECT * FROM invoice_archive WHERE id=?",input.id());
        if(!existing.isEmpty()){var row=existing.getFirst();boolean same=row.get("uploaded_by").equals(user.id())&&Objects.equals(row.get("client_id"),input.clientId())&&row.get("customer_name").equals(input.customer().trim())&&row.get("reference").equals(input.reference().trim())&&((java.sql.Date)row.get("issued_on")).toLocalDate().equals(input.date())&&row.get("description").equals(input.description().trim())&&row.get("document_type").equals(input.type())&&((Number)row.get("gross_cents")).longValue()==input.grossCents()&&Objects.equals(row.get("net_cents")==null?null:((Number)row.get("net_cents")).longValue(),input.netCents())&&row.get("payment_status").equals(input.status())&&row.get("filename").equals(input.filename())&&Arrays.equals((byte[])row.get("data"),bytes);if(same)return Map.of("id",input.id());throw new ResponseStatusException(HttpStatus.CONFLICT);}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM invoice_archive WHERE LOWER(reference)=LOWER(?)",Integer.class,input.reference().trim())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze factuurreferentie staat al in CAP.");
        if(jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM invoice_archive",Long.class)+bytes.length>262144000)throw new ResponseStatusException(HttpStatus.CONFLICT,"Het factuurarchief heeft zijn opslaglimiet bereikt.");
        jdbc.update("INSERT INTO invoice_archive(id,client_id,customer_name,reference,issued_on,description,document_type,gross_cents,net_cents,payment_status,filename,size_bytes,data,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",input.id(),input.clientId(),input.customer().trim(),input.reference().trim(),input.date(),input.description().trim(),input.type(),input.grossCents(),input.netCents(),input.status(),input.filename(),bytes.length,bytes,user.id());
        policy.audit(user,"INVOICE_ARCHIVE_UPLOAD",input.id().toString(),input.reference().trim());return Map.of("id",input.id());
    }
    @GetMapping("/{id}/file") public ResponseEntity<byte[]> file(Principal p,@PathVariable UUID id){reader(p);var rows=jdbc.queryForList("SELECT filename,data FROM invoice_archive WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);var row=rows.getFirst();return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename((String)row.get("filename"),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body((byte[])row.get("data"));}
    @PutMapping("/{id}/status") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(Principal p,@PathVariable UUID id,@RequestBody Status input){var user=owner(p);if(input.status()==null||!Set.of("open","paid").contains(input.status())||input.version()==null||input.version()<1)throw invalid();var rows=jdbc.queryForList("SELECT version FROM invoice_archive WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);owner(p);if(((Number)rows.getFirst().get("version")).intValue()!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT);jdbc.update("UPDATE invoice_archive SET payment_status=?,version=version+1 WHERE id=?",input.status(),id);policy.audit(user,"INVOICE_ARCHIVE_STATUS",id.toString(),input.status());}
}
