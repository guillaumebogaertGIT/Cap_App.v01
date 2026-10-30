-- Display titles are independent of security roles and review permissions.
ALTER TABLE cap_user ADD COLUMN coach_title VARCHAR(100) NOT NULL DEFAULT 'Coach';
UPDATE cap_user SET coach_title='Admin · Trainer' WHERE role='COACH' AND email='hello@cap-gistel.be';
UPDATE cap_user SET coach_title='Manager · Trainer' WHERE role='COACH' AND email IN ('dieter.vlamynck@gmail.com','mmarchand@hotmail.be');
UPDATE cap_user SET coach_title='Assistent · Trainer' WHERE role='COACH' AND email IN ('marliesvermeesch@hotmail.com','robinvgeluwe@gmail.com');
UPDATE cap_user SET coach_title='Standaard · Trainer' WHERE role='COACH' AND email='vince.decraemer@hotmail.com';
