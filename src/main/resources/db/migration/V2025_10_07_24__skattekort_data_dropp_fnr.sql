SET lock_timeout = '5s';

ALTER TABLE skattekort_data DROP COLUMN IF EXISTS fnr;
/* fnr finnes i den lagrede teksten. Kjapp diskusjon med Steinar
   fikk Geir og Steinar til å konkludere med at vi slår opp i denne tabellen når
   det potensialt er noe feil med koden vår. Geirs tolkning er derfor bør vi slå opp
   på fnr ved å se på den lagrede teksten heller enn å bruke våre
   parsede verdier.
 */