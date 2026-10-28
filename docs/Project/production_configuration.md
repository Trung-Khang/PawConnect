# Production configuration

This file documents variable names only. Store their values in Railway service variables or a local ignored environment file, never in Git.

## Backend service variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE=production,mysql` | Yes | Activates hardened production settings and the MySQL datasource. |
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | Yes | MySQL connection. For a Railway backend service, use variable references to the MySQL service over its private network. The application will not create a missing database. |
| `RAILPACK_JDK_VERSION=17` | Yes on Railway | Matches the Java version in `pom.xml`; Railpack otherwise defaults to 21. |
| `JWT_SECRET` | Yes | Unique random secret of at least 32 bytes. Rotate it if it has ever appeared in a document, terminal capture, or commit. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | Yes | SMTP delivery for registration and password-reset OTP. |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Yes | Avatar and adoption-media storage. |
| `WEBSOCKET_ALLOWED_ORIGIN_PATTERN` | Yes | Exact public frontend origin, for example `https://example.com`; avoid a wildcard. |
| `JPA_DDL_AUTO=validate` | Recommended | Explicitly preserves the production schema-validation policy. |

Railway injects `PORT`; the application binds to it, or to `8080` when `PORT` is absent for local runs. Set `MYSQL_HOST=${{MySQL.MYSQLHOST}}`, `MYSQL_PORT=${{MySQL.MYSQLPORT}}`, `MYSQL_DATABASE=${{MySQL.MYSQLDATABASE}}`, `MYSQL_USER=${{MySQL.MYSQLUSER}}` and `MYSQL_PASSWORD=${{MySQL.MYSQLPASSWORD}}` using the actual MySQL service name. Check the database name and the availability of private-network variables in the Railway project before deploying. Do not copy the public TCP proxy host into the backend service.

## Railway procedure

1. Create or select the **backend** service in the same Railway project as MySQL. Do not put application variables on the MySQL service.
2. Add the variables above to the backend service. Reference the MySQL service variables rather than copying passwords into a shared document.
3. Keep `app.cloudinary.enabled` enabled only with all three Cloudinary variables available; the production profile fails fast otherwise.
4. Deploy the backend, inspect startup logs for successful schema validation, then perform the manual Auth and media E2E checklist.
5. Never run seed import automatically against the shared `pawconnect` database. Run reviewed migrations separately and record the evidence.
