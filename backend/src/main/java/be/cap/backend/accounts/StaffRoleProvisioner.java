package be.cap.backend.accounts;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Offline, explicit initial activation only. Never called at startup or through HTTP. */
public final class StaffRoleProvisioner {
    public record Mapping(UUID id,String email,String role){}
    public static void activate(Connection connection,List<Mapping> mappings,boolean apply) throws SQLException {
        if(mappings.isEmpty())throw new IllegalArgumentException("Empty mapping");
        var ids=new HashSet<UUID>();
        connection.setReadOnly(!apply);
        connection.setAutoCommit(false);
        try {
            boolean hasRole;
            try(var query=connection.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='cap_user' AND column_name='staff_role'");var rows=query.executeQuery()){rows.next();hasRole=rows.getInt(1)==1;}
            if(apply && !hasRole)throw new IllegalStateException("Apply migration V13 only after backup and approval");
            if(apply)try(var query=connection.prepareStatement("SELECT id FROM cap_user WHERE role='COACH' ORDER BY id FOR UPDATE")){query.executeQuery().close();}
            for(var value:mappings) {
                if(value.id()==null || !ids.add(value.id()) || value.email()==null || !Set.of("OWNER","MANAGER","INSTRUCTOR","FINANCE","DEVELOPMENT").contains(value.role()))throw new IllegalArgumentException("Invalid mapping");
                try(var query=connection.prepareStatement("SELECT email,role,active"+(hasRole?",staff_role":"")+" FROM cap_user WHERE id=?")) {
                    query.setObject(1,value.id());try(var row=query.executeQuery()) {
                        if(!row.next() || !row.getString("email").equals(value.email()) || !row.getString("role").equals("COACH") || !row.getBoolean("active"))throw new IllegalArgumentException("Account identifier mismatch");
                        String previous=hasRole?row.getString("staff_role"):"UNASSIGNED";
                        if(!previous.equals("UNASSIGNED") && !previous.equals(value.role()))throw new IllegalStateException("Existing role differs; use authenticated role management");
                        System.out.println(value.id()+" | "+value.email()+" | "+previous+" -> "+value.role());
                        if(apply && previous.equals("UNASSIGNED")) {
                            try(var update=connection.prepareStatement("UPDATE cap_user SET staff_role=? WHERE id=?")){update.setString(1,value.role());update.setObject(2,value.id());update.executeUpdate();}
                            try(var audit=connection.prepareStatement("INSERT INTO staff_audit(id,actor_id,action,target_id,detail,created_at) VALUES(?,NULL,'STAFF_ACTIVATION',?,?,CURRENT_TIMESTAMP)")){
                                audit.setObject(1,UUID.randomUUID());audit.setString(2,value.id().toString());audit.setString(3,value.role());audit.executeUpdate();}
                        }
                    }
                }
            }
            if(apply) {
                try(var query=connection.prepareStatement("SELECT COUNT(*) FROM cap_user WHERE role='COACH' AND staff_role='OWNER' AND active=TRUE");var row=query.executeQuery()){row.next();if(row.getInt(1)<1)throw new IllegalStateException("An active owner is required");}
                connection.commit();
            } else connection.rollback();
        }catch(SQLException|RuntimeException error){connection.rollback();throw error;}
    }
    public static void main(String[] args) throws Exception {
        if(args.length<1 || args.length>2 || args.length==2&&!args[1].equals("--apply-approved"))throw new IllegalArgumentException("Mapping file, optional --apply-approved");
        String url=System.getenv("CAP_DATABASE_URL");
        if(url==null || !url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/cap(_test[a-zA-Z0-9_]*)?"))throw new IllegalArgumentException("Local CAP database only");
        var mappings=Arrays.asList(JsonMapper.builder().build().readValue(Files.readString(Path.of(args[0])),Mapping[].class));
        try(var connection=DriverManager.getConnection(url,System.getenv("CAP_DATABASE_USER"),System.getenv("CAP_DATABASE_PASSWORD"))){activate(connection,mappings,args.length==2);}
        catch(Exception error){System.err.println("Role activation failed. Transaction rolled back; verify IDs, backup, migration and approval. No credentials printed.");System.exit(1);}
    }
}
