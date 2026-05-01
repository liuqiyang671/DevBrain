# Database Resources

`schema.sql` is the Step 02 local database baseline. It enables pgvector and creates `t_devbrain_schema_info` so the initialized database can record which setup step has run.

Business tables are intentionally left to later build steps, for example user authentication, knowledge-base CRUD, vector storage, trace, and ingestion pipeline modules.

Run manually when needed:

```powershell
docker exec devbrain-postgres psql -U devbrain -d devbrain -f /docker-entrypoint-initdb.d/01-schema.sql
```
