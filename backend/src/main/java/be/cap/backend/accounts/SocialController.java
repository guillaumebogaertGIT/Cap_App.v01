package be.cap.backend.accounts;

import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("accounts") @RequestMapping("/api/social")
public class SocialController {
    public record Attachment(String name,String data){}
    public record Input(UUID id,String content,Attachment attachment,String channel){}
    private static final Map<String,String> CHANNELS;
    static {var channels=new LinkedHashMap<String,String>();channels.put("general","Algemeen");channels.put("hyrox","CAP HYROX");channels.put("hybrid","CAP Hybrid");channels.put("move","CAP Move");channels.put("lift-hit","CAP Lift & HIT");channels.put("open-gym","Open gym");CHANNELS=Collections.unmodifiableMap(channels);}
    private String channel(String value){String result=value==null?"general":value;if(!CHANNELS.containsKey(result))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dit kanaal bestaat niet.");return result;}
    @GetMapping("/community/channels") public ResponseEntity<?> channels(Principal p){
        var user=actor(p);var counts=new HashMap<String,Long>();
        for(var row:jdbc.queryForList("SELECT m.channel,COUNT(*) AS unread FROM social_message m WHERE m.kind='community' AND m.deleted=FALSE AND m.author_id<>? AND NOT EXISTS(SELECT 1 FROM social_notification_read r WHERE r.message_id=m.id AND r.user_id=?) GROUP BY m.channel",user.id(),user.id()))counts.put((String)row.get("channel"),((Number)row.get("unread")).longValue());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CHANNELS.entrySet().stream().map(entry->Map.of("id",entry.getKey(),"name",entry.getValue(),"unread",counts.getOrDefault(entry.getKey(),0L))).toList());
    }
    public record Privacy(Boolean discoverable){}
    public record Read(List<UUID> ids){}
    private Map<String,Object> thread(UUID user,UUID id,String kind,String name){
        String where=kind.equals("direct")?"kind='direct' AND ((author_id=? AND recipient_id=?) OR (author_id=? AND recipient_id=?))":"kind='group' AND session_id=?";
        Object[] params=kind.equals("direct")?new Object[]{user,id,id,user}:new Object[]{id};
        var last=jdbc.queryForList("SELECT id,content,created_at,deleted FROM social_message WHERE "+where+" ORDER BY created_at DESC,id DESC LIMIT 1",params);
        String unreadWhere=kind.equals("direct")?"m.kind='direct' AND m.author_id=? AND m.recipient_id=?":"m.kind='group' AND m.session_id=? AND m.author_id<>?";
        Long unread=jdbc.queryForObject("SELECT COUNT(*) FROM social_message m WHERE "+unreadWhere+" AND m.deleted=FALSE AND NOT EXISTS(SELECT 1 FROM social_notification_read r WHERE r.message_id=m.id AND r.user_id=?)",Long.class,id,user,user);
        var value=new LinkedHashMap<String,Object>();value.put("id",id);value.put("kind",kind);value.put("name",name);value.put("unread",unread);
        value.put("preview",last.isEmpty()?"Start een gesprek":Boolean.TRUE.equals(last.getFirst().get("deleted"))?"Bericht verwijderd.":(last.getFirst().get("content").toString().isBlank()?"Bestand":last.getFirst().get("content")));
        value.put("updated",last.isEmpty()?null:last.getFirst().get("created_at"));
        if(kind.equals("direct")&&jdbc.queryForObject("SELECT COUNT(*) FROM account_profile WHERE user_id=? AND photo IS NOT NULL",Integer.class,id)>0)value.put("photoUrl","/api/social/contacts/"+id+"/photo");return value;
    }
    @GetMapping("/contacts/{id}/photo") public ResponseEntity<byte[]> contactPhoto(Principal p,@PathVariable UUID id){
        var user=actor(p);if(!direct(user.id(),id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var rows=jdbc.query("SELECT photo FROM account_profile WHERE user_id=?",(row,index)->row.getBytes(1),id);
        if(rows.isEmpty()||rows.getFirst()==null)return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(rows.getFirst());
    }
    @GetMapping("/community/authors/{id}/photo") public ResponseEntity<byte[]> communityPhoto(Principal p,@PathVariable UUID id){
        actor(p);
        var rows=jdbc.query("SELECT p.photo FROM account_profile p JOIN cap_user u ON u.id=p.user_id WHERE p.user_id=? AND u.active=TRUE AND EXISTS(SELECT 1 FROM social_message m WHERE m.author_id=p.user_id AND m.kind='community' AND m.deleted=FALSE)",(row,index)->row.getBytes(1),id);
        if(rows.isEmpty()||rows.getFirst()==null)return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(rows.getFirst());
    }
    @GetMapping("/inbox") public ResponseEntity<?> inbox(Principal p){
        var user=actor(p);var threads=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT id,display_name FROM cap_user WHERE active=TRUE AND id<>? ORDER BY display_name",user.id()))if(direct(user.id(),(UUID)row.get("id")))threads.add(thread(user.id(),(UUID)row.get("id"),"direct",(String)row.get("display_name")));
        boolean owner=policy.role(user).equals("OWNER");
        for(var row:jdbc.queryForList("SELECT s.id,s.title,s.session_date FROM club_session s WHERE s.session_type='Groep' AND (?=TRUE OR s.coach_id=? OR EXISTS(SELECT 1 FROM session_booking b WHERE b.session_id=s.id AND b.athlete_id=? AND b.cancelled=FALSE)) ORDER BY s.session_date DESC LIMIT 100",owner,user.id(),user.id())){var value=thread(user.id(),(UUID)row.get("id"),"group",(String)row.get("title"));value.put("date",row.get("session_date"));threads.add(value);}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("threads",threads,"discoverable",opted(user.id())));
    }
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;
    public SocialController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;}
    private boolean eligible(UUID id){return jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND active=TRUE AND (role='ATHLETE' OR staff_role IN ('OWNER','MANAGER','INSTRUCTOR'))",Integer.class,id)==1;}
    private AccountService.Account actor(Principal p){var user=accounts.account(p.getName());if(!eligible(user.id()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return user;}
    private boolean opted(UUID id){return jdbc.queryForObject("SELECT COUNT(*) FROM chat_preferences WHERE user_id=? AND discoverable=TRUE",Integer.class,id)==1;}
    private boolean direct(UUID one,UUID two){
        if(one.equals(two)||!eligible(one)||!eligible(two))return false;
        var roles=jdbc.queryForList("SELECT id,role,staff_role FROM cap_user WHERE id=? OR id=?",one,two);
        var first=roles.stream().filter(r->r.get("id").equals(one)).findFirst().orElseThrow();var second=roles.stream().filter(r->r.get("id").equals(two)).findFirst().orElseThrow();
        if(first.get("role").equals("ATHLETE")&&second.get("role").equals("ATHLETE"))return opted(one)&&opted(two);
        if(first.get("role").equals("COACH")&&second.get("role").equals("COACH"))return true;
        var coach=first.get("role").equals("COACH")?first:second;var client=coach==first?second:first;
        return Set.of("OWNER","MANAGER").contains(coach.get("staff_role"))||jdbc.queryForObject("SELECT COUNT(*) FROM instructor_client WHERE instructor_id=? AND client_id=?",Integer.class,coach.get("id"),client.get("id"))>0;
    }
    private void requireDirect(UUID one,UUID two){if(!direct(one,two))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Dit contact is niet beschikbaar. Controleer coachkoppeling of chatvoorkeuren.");}
    private void requireGroup(AccountService.Account user,UUID session){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM club_session WHERE id=? AND session_type='Groep'",Integer.class,session)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(policy.role(user).equals("OWNER"))return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM club_session WHERE id=? AND coach_id=?",Integer.class,session,user.id())>0)return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM session_booking WHERE session_id=? AND athlete_id=? AND cancelled=FALSE",Integer.class,session,user.id())==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private List<Map<String,Object>> messages(String where,Object...args){return jdbc.queryForList("SELECT * FROM (SELECT m.id,m.author_id AS author,u.display_name AS name,m.content,m.created_at AS created,m.deleted,a.filename,a.size_bytes AS size,CASE WHEN m.kind='community' AND m.deleted=FALSE AND u.active=TRUE AND EXISTS(SELECT 1 FROM account_profile p WHERE p.user_id=m.author_id AND p.photo IS NOT NULL) THEN '/api/social/community/authors/' || CAST(m.author_id AS VARCHAR) || '/photo' ELSE NULL END AS photo_url FROM social_message m JOIN cap_user u ON u.id=m.author_id LEFT JOIN social_attachment a ON a.message_id=m.id "+where+" ORDER BY m.created_at DESC,m.id DESC LIMIT 100) recent ORDER BY created,id",args);}
    @GetMapping("/attachments/{id}") public ResponseEntity<byte[]> attachment(Principal p,@PathVariable UUID id){
        var user=actor(p);var rows=jdbc.queryForList("SELECT * FROM social_message WHERE id=? AND deleted=FALSE",id);
        if(rows.isEmpty()||!canRead(user,rows.getFirst()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var files=jdbc.queryForList("SELECT * FROM social_attachment WHERE message_id=?",id);if(files.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var file=files.getFirst();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename((String)file.get("filename"),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM).body((byte[])file.get("data"));
    }
    private byte[] validateAttachment(Attachment file){
        if(file==null)return null;
        if(file.name()==null||file.name().length()>180||!file.name().matches("[^\\\\/\\p{Cntrl}]+\\.(?i:pdf|png|jpg|jpeg|webp|txt|csv|docx|xlsx|pptx)")||file.data()==null||file.data().length()>13981016)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een PDF, afbeelding of document van maximaal 10 MB.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(file.data());}catch(IllegalArgumentException error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
        if(bytes.length==0||bytes.length>10485760)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Het bestand is leeg of groter dan 10 MB.");
        String ext=file.name().substring(file.name().lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String head=new String(bytes,0,Math.min(bytes.length,12),java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean valid=switch(ext){case "pdf"->head.startsWith("%PDF-");case "png"->head.startsWith("\u0089PNG\r\n\u001a\n");case "jpg","jpeg"->head.startsWith("\u00ff\u00d8\u00ff");case "webp"->head.startsWith("RIFF")&&head.endsWith("WEBP");case "docx","xlsx","pptx"->officeFile(bytes,ext);default->!head.contains("\u0000");};
        if(!valid)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dit bestand komt niet overeen met het bestandstype.");return bytes;
    }
    private boolean officeFile(byte[] bytes,String ext){
        String prefix=switch(ext){case "docx"->"word/";case "xlsx"->"xl/";default->"ppt/";};boolean types=false,document=false;
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))){long total=0;int entries=0;java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];while((entry=zip.getNextEntry())!=null){if(++entries>2000)return false;String name=entry.getName();if(name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"))return false;types|=name.equals("[Content_Types].xml");document|=name.startsWith(prefix);int count;while((count=zip.read(buffer))!=-1){total+=count;if(total>52428800)return false;}}return types&&document;}catch(java.io.IOException error){return false;}
    }
    private boolean canRead(AccountService.Account user,Map<String,Object> message){
        if(message.get("kind").equals("community"))return true;
        if(message.get("kind").equals("direct")){
            var author=(UUID)message.get("author_id");var recipient=(UUID)message.get("recipient_id");
            return (user.id().equals(author)||user.id().equals(recipient))&&direct(author,recipient);
        }
        try{requireGroup(user,(UUID)message.get("session_id"));return true;}catch(ResponseStatusException denied){return false;}
    }
    @GetMapping("/notifications")
    public ResponseEntity<?> notifications(Principal p){
        var user=actor(p);
        var candidates=jdbc.queryForList("""
            SELECT m.*,u.display_name AS name FROM social_message m JOIN cap_user u ON u.id=m.author_id
            WHERE m.deleted=FALSE AND m.author_id<>?
            AND NOT EXISTS(SELECT 1 FROM social_notification_read r WHERE r.message_id=m.id AND r.user_id=?)
            AND (m.kind='community' OR (m.kind='direct' AND m.recipient_id=?) OR
                (m.kind='group' AND EXISTS(SELECT 1 FROM club_session s WHERE s.id=m.session_id AND s.session_type='Groep'
                    AND (?=TRUE OR s.coach_id=? OR EXISTS(SELECT 1 FROM session_booking b WHERE b.session_id=s.id AND b.athlete_id=? AND b.cancelled=FALSE)))))
            ORDER BY m.created_at DESC,m.id DESC LIMIT 100
            """,user.id(),user.id(),user.id(),policy.role(user).equals("OWNER"),user.id(),user.id());
        var items=new ArrayList<Map<String,Object>>();long messages=0;
        var scopes=new HashMap<String,Boolean>();
        for(var row:candidates){String scope=row.get("kind")+":"+(row.get("kind").equals("group")?row.get("session_id"):row.get("author_id"));if(!scopes.computeIfAbsent(scope,key->canRead(user,row)))continue;var item=new LinkedHashMap<String,Object>();item.put("id",row.get("id"));item.put("kind",row.get("kind"));item.put("name",row.get("name"));String content=(String)row.get("content");if(content.isBlank())content="Bestand";item.put("excerpt",content.substring(0,Math.min(content.length(),120)));item.put("created",row.get("created_at"));if(row.get("kind").equals("community")){item.put("channel",row.get("channel"));item.put("channelName",CHANNELS.get((String)row.get("channel")));}if(row.get("kind").equals("direct"))item.put("contactId",row.get("author_id"));if(row.get("kind").equals("group"))item.put("sessionId",row.get("session_id"));items.add(item);if(!row.get("kind").equals("community"))messages++;}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items",items,"unread",items.size(),"messageUnread",messages));
    }
    @PutMapping("/notifications/read") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Principal p,@RequestBody Read input){
        var user=actor(p);if(input.ids()==null||input.ids().size()>100||input.ids().contains(null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),user.id());actor(p);
        for(var id:new HashSet<>(input.ids())){
            var rows=jdbc.queryForList("SELECT * FROM social_message WHERE id=?",id);
            if(rows.isEmpty()||!canRead(user,rows.getFirst()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM social_notification_read WHERE message_id=? AND user_id=?",Integer.class,id,user.id())==0)jdbc.update("INSERT INTO social_notification_read(message_id,user_id) VALUES(?,?)",id,user.id());
        }
    }
    @DeleteMapping("/notifications/read") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unread(Principal p,@RequestBody Read input){
        var user=actor(p);if(input.ids()==null||input.ids().size()>100||input.ids().contains(null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),user.id());actor(p);
        for(var id:new HashSet<>(input.ids())){var rows=jdbc.queryForList("SELECT * FROM social_message WHERE id=?",id);if(rows.isEmpty()||!canRead(user,rows.getFirst()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);jdbc.update("DELETE FROM social_notification_read WHERE message_id=? AND user_id=?",id,user.id());}
    }
    @GetMapping("/community") public ResponseEntity<?> feed(Principal p,@RequestParam(required=false) String channel){actor(p);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messages("WHERE kind='community' AND channel=?",channel(channel)));}
    @GetMapping("/contacts") public ResponseEntity<?> contacts(Principal p){var user=actor(p);var people=jdbc.queryForList("SELECT id,display_name AS name,role FROM cap_user WHERE active=TRUE AND id<>?",user.id()).stream().filter(row->direct(user.id(),(UUID)row.get("id"))).toList();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("discoverable",opted(user.id()),"contacts",people));}
    @PutMapping("/privacy") @Transactional public Map<String,Boolean> privacy(Principal p,@RequestBody Privacy input){var user=actor(p);if(input.discoverable()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),user.id());if(jdbc.update("UPDATE chat_preferences SET discoverable=? WHERE user_id=?",input.discoverable(),user.id())==0)jdbc.update("INSERT INTO chat_preferences(user_id,discoverable) VALUES(?,?)",user.id(),input.discoverable());return Map.of("discoverable",input.discoverable());}
    @GetMapping("/direct/{id}") public ResponseEntity<?> conversation(Principal p,@PathVariable UUID id){var user=actor(p);requireDirect(user.id(),id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messages("WHERE kind='direct' AND ((author_id=? AND recipient_id=?) OR (author_id=? AND recipient_id=?))",user.id(),id,id,user.id()));}
    @GetMapping("/groups") public ResponseEntity<?> groups(Principal p){var user=actor(p);boolean owner=policy.role(user).equals("OWNER");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.queryForList("SELECT s.id,s.title,s.session_date AS date,s.start_time AS start FROM club_session s WHERE s.session_type='Groep' AND (?=TRUE OR s.coach_id=? OR EXISTS(SELECT 1 FROM session_booking b WHERE b.session_id=s.id AND b.athlete_id=? AND b.cancelled=FALSE)) ORDER BY s.session_date DESC,s.start_time DESC LIMIT 100",owner,user.id(),user.id()));}
    @GetMapping("/groups/{id}") public ResponseEntity<?> group(Principal p,@PathVariable UUID id){var user=actor(p);requireGroup(user,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(messages("WHERE kind='group' AND session_id=?",id));}
    private Map<String,Object> post(AccountService.Account user,Input input,String kind,UUID recipient,UUID session){
        String channel=kind.equals("community")?channel(input.channel()):null;
        if(input.id()==null||input.content()==null||(input.content().isBlank()&&input.attachment()==null)||input.content().length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Schrijf een bericht of voeg een bestand toe (maximaal 2000 tekens).");
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),user.id());
        actor(()->user.email());
        if(kind.equals("direct"))requireDirect(user.id(),recipient);if(kind.equals("group"))requireGroup(user,session);
        byte[] bytes=validateAttachment(input.attachment());
        var existing=jdbc.queryForList("SELECT * FROM social_message WHERE id=?",input.id());
        if(!existing.isEmpty()){var row=existing.getFirst();var files=jdbc.queryForList("SELECT filename,data FROM social_attachment WHERE message_id=?",input.id());boolean sameFile=bytes==null?files.isEmpty():!files.isEmpty()&&files.getFirst().get("filename").equals(input.attachment().name())&&Arrays.equals(bytes,(byte[])files.getFirst().get("data"));if(!Boolean.TRUE.equals(row.get("deleted"))&&sameFile&&row.get("author_id").equals(user.id())&&Objects.equals(row.get("recipient_id"),recipient)&&Objects.equals(row.get("session_id"),session)&&row.get("kind").equals(kind)&&Objects.equals(row.get("channel"),channel)&&row.get("content").equals(input.content().trim()))return Map.of("id",input.id());throw new ResponseStatusException(HttpStatus.CONFLICT);}
        if(bytes!=null&&jdbc.queryForObject("SELECT COALESCE(SUM(a.size_bytes),0) FROM social_attachment a JOIN social_message m ON m.id=a.message_id WHERE m.author_id=?",Long.class,user.id())+bytes.length>104857600)throw new ResponseStatusException(HttpStatus.CONFLICT,"Je bestandsruimte is vol (100 MB). Verwijder eerst oude bijlagen.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM social_message WHERE author_id=? AND created_at>?",Integer.class,user.id(),java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)))>=20)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Je verstuurt veel berichten. Wacht even.");
        jdbc.update("INSERT INTO social_message(id,author_id,recipient_id,session_id,kind,content,channel) VALUES(?,?,?,?,?,?,?)",input.id(),user.id(),recipient,session,kind,input.content().trim(),channel);if(bytes!=null)jdbc.update("INSERT INTO social_attachment(message_id,filename,size_bytes,data) VALUES(?,?,?,?)",input.id(),input.attachment().name(),bytes.length,bytes);return Map.of("id",input.id());
    }
    @PostMapping("/community") @Transactional public Map<String,Object> postCommunity(Principal p,@RequestBody Input input){return post(actor(p),input,"community",null,null);}
    @PostMapping("/direct/{id}") @Transactional public Map<String,Object> postDirect(Principal p,@PathVariable UUID id,@RequestBody Input input){return post(actor(p),input,"direct",id,null);}
    @PostMapping("/groups/{id}") @Transactional public Map<String,Object> postGroup(Principal p,@PathVariable UUID id,@RequestBody Input input){return post(actor(p),input,"group",null,id);}
    @DeleteMapping("/messages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void delete(Principal p,@PathVariable UUID id){var user=actor(p);var rows=jdbc.queryForList("SELECT * FROM social_message WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);var row=rows.getFirst();if(row.get("kind").equals("direct")&&!row.get("author_id").equals(user.id())&&!row.get("recipient_id").equals(user.id()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(!row.get("author_id").equals(user.id())&&!policy.role(user).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(!policy.role(user).equals("OWNER")){if(row.get("kind").equals("direct"))requireDirect(user.id(),(UUID)row.get("recipient_id"));if(row.get("kind").equals("group"))requireGroup(user,(UUID)row.get("session_id"));}jdbc.update("DELETE FROM social_attachment WHERE message_id=?",id);jdbc.update("UPDATE social_message SET content='',deleted=TRUE WHERE id=?",id);policy.audit(user,"SOCIAL_DELETE",id.toString(),(String)row.get("kind"));}
}
