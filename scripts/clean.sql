DO $$
DECLARE 
    brow record;
BEGIN
    FOR brow IN (select 'drop table "' || tablename || '" cascade;' as table_name from pg_tables where schemaname = 'public') LOOP
        EXECUTE brow.table_name;
    END LOOP;
END; $$;
    
