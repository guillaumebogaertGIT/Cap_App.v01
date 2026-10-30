package be.cap.backend;

import be.cap.backend.accounts.StaffRoleProvisioner;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:staff-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class StaffMigrationTests extends AccountApiTests {
    @Test void v12ToV13PreservesExistingDataAndInfersNoRealRoleOrHistoricalAuthor() {
        var source=Objects.requireNonNull(jdbc.getDataSource());String schema="cap_test_migration_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("12").load().migrate();
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO "+schema+".cap_user(id,email,display_name,password_hash,role,created_at) VALUES(?,'hello@cap-gistel.be','Glenn','FAKE-UNCHANGED-HASH','COACH',CURRENT_TIMESTAMP)",id);
        String legacy="{\"workouts\":[],\"assignments\":[{\"assignmentId\":\"old-assignment\",\"note\":\"Legacy text with unknown author/date\"}]}";
        jdbc.update("UPDATE "+schema+".workout_state SET payload=? WHERE id=1",legacy);
        jdbc.update("INSERT INTO "+schema+".account_profile(user_id,values_json) VALUES(?,?)",id,"{\"weightKg\":80}");
        jdbc.update("INSERT INTO "+schema+".billing_account(user_id,customer_key,customer_started_at) VALUES(?,?,CURRENT_TIMESTAMP)",id,UUID.randomUUID());
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        assertEquals("UNASSIGNED",jdbc.queryForObject("SELECT staff_role FROM "+schema+".cap_user WHERE id=?",String.class,id));
        assertEquals("FAKE-UNCHANGED-HASH",jdbc.queryForObject("SELECT password_hash FROM "+schema+".cap_user WHERE id=?",String.class,id));
        assertEquals(legacy,jdbc.queryForObject("SELECT payload FROM "+schema+".workout_state WHERE id=1",String.class));
        assertEquals("{\"weightKg\":80}",jdbc.queryForObject("SELECT values_json FROM "+schema+".account_profile WHERE user_id=?",String.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".billing_account",Integer.class));
        for(String table:List.of("staff_note","staff_audit","assignment_attribution","instructor_client"))assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+"."+table,Integer.class));
    }
    @Test void offlineActivationRequiresExactIdsAndEmailsAndRollsBackWholeBatch() throws Exception {
        var source=Objects.requireNonNull(jdbc.getDataSource());UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        for(UUID id:List.of(first,second))jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at) VALUES(?,?,'Fake staff','FAKE','COACH',CURRENT_TIMESTAMP)",id,id+"@example.test");
        var valid=new StaffRoleProvisioner.Mapping(first,first+"@example.test","INSTRUCTOR");
        try(var connection=source.getConnection()){StaffRoleProvisioner.activate(connection,List.of(valid),false);}
        assertEquals("UNASSIGNED",jdbc.queryForObject("SELECT staff_role FROM cap_user WHERE id=?",String.class,first));
        try(var connection=source.getConnection()){assertThrows(IllegalArgumentException.class,()->StaffRoleProvisioner.activate(connection,List.of(valid,new StaffRoleProvisioner.Mapping(second,"wrong@example.test","FINANCE")),true));}
        assertEquals("UNASSIGNED",jdbc.queryForObject("SELECT staff_role FROM cap_user WHERE id=?",String.class,first));
        try(var connection=source.getConnection()){StaffRoleProvisioner.activate(connection,List.of(valid),true);StaffRoleProvisioner.activate(connection,List.of(valid),true);}
        assertEquals("INSTRUCTOR",jdbc.queryForObject("SELECT staff_role FROM cap_user WHERE id=?",String.class,first));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM staff_audit WHERE action='STAFF_ACTIVATION' AND target_id=?",Integer.class,first.toString()));
    }
}
