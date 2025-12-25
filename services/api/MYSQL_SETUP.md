# MySQL Database Setup Guide

This guide explains how to set up and use MySQL with the HookHub API Gateway Service.

## Prerequisites

1. **MySQL Server** installed and running on your local machine
   - Download from: https://dev.mysql.com/downloads/mysql/
   - Or use Homebrew on macOS: `brew install mysql`
   - Start MySQL service: `brew services start mysql` (macOS) or `sudo systemctl start mysql` (Linux)

2. **Create the Database**

   Connect to MySQL and create the database:
   ```sql
   mysql -u root -p
   CREATE DATABASE webhookdb;
   ```

   Or use the automatic creation feature (configured in `application.properties`):
   - The `createDatabaseIfNotExist=true` parameter will automatically create the database if it doesn't exist.

## Configuration

The MySQL configuration is already set up in `application.properties`:

```properties
# MySQL Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/webhookdb?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=
```

### Customizing Database Credentials

If your MySQL setup uses different credentials, update `application.properties`:

```properties
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Customizing Database Name or Port

If you want to use a different database name or MySQL port:

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/webhookdb?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

## Database Schema

The application uses JPA/Hibernate with `ddl-auto=update`, which means:

- **Tables are automatically created** on first run
- **Schema is updated** automatically when entities change
- **No manual migration scripts needed** for development

### Tables Created

1. **webhooks** table:
   - `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
   - `url` (VARCHAR, NOT NULL)
   - `metadata` (TEXT)
   - `created_at` (TIMESTAMP, NOT NULL)
   - `updated_at` (TIMESTAMP, NOT NULL)

2. **events** table:
   - `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
   - `webhook_id` (BIGINT, NOT NULL)
   - `payload` (TEXT)
   - `status` (VARCHAR, NOT NULL) - Enum: PENDING, PROCESSING, COMPLETED, FAILED, PAUSED
   - `created_at` (TIMESTAMP, NOT NULL)
   - `updated_at` (TIMESTAMP, NOT NULL)

## Running the Application

1. **Ensure MySQL is running**:
   ```bash
   # Check MySQL status
   mysqladmin -u root -p status
   ```

2. **Start the Spring Boot application**:
   ```bash
   cd services/api
   mvn spring-boot:run
   ```

3. **Verify database connection**:
   - Check application logs for: "HikariPool-1 - Starting..."
   - If you see connection errors, verify MySQL is running and credentials are correct

## Testing the Database

### Using MySQL Command Line

```sql
mysql -u root -p webhookdb

-- View all webhooks
SELECT * FROM webhooks;

-- View all events
SELECT * FROM events;

-- View events by status
SELECT * FROM events WHERE status = 'PENDING';

-- View events for a specific webhook
SELECT * FROM events WHERE webhook_id = 1;
```

### Using API Endpoints

```bash
# Register a webhook (creates entry in webhooks table)
curl -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/webhook", "metadata": {"key": "value"}}'

# List all webhooks (reads from webhooks table)
curl http://localhost:8080/webhooks

# Create an event (creates entry in events table)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"webhookId": 1, "payload": {"event": "test"}}'

# List all events (reads from events table)
curl http://localhost:8080/events

# List events for a specific webhook
curl http://localhost:8080/events?webhookId=1
```

## Connection Pool Configuration

The application uses HikariCP connection pool with the following settings:

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
```

These can be adjusted in `application.properties` based on your needs.

## Troubleshooting

### Connection Refused Error

```
Communications link failure
```

**Solution**: Ensure MySQL server is running:
```bash
# macOS
brew services start mysql

# Linux
sudo systemctl start mysql

# Windows
net start MySQL
```

### Access Denied Error

```
Access denied for user 'root'@'localhost'
```

**Solution**: Update credentials in `application.properties` or create a MySQL user:
```sql
CREATE USER 'hookhub'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON webhookdb.* TO 'hookhub'@'localhost';
FLUSH PRIVILEGES;
```

Then update `application.properties`:
```properties
spring.datasource.username=hookhub
spring.datasource.password=password
```

### Database Doesn't Exist Error

**Solution**: The `createDatabaseIfNotExist=true` parameter should handle this automatically. If not, create manually:
```sql
CREATE DATABASE webhookdb;
```

### Timezone Issues

If you encounter timezone-related errors, ensure your MySQL timezone is set:
```sql
SET GLOBAL time_zone = '+00:00';
```

Or update the connection URL in `application.properties` to use your local timezone.

## Production Considerations

For production deployments:

1. **Change `ddl-auto` to `validate` or `none`**:
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   ```

2. **Use proper database credentials** (not root user)

3. **Enable SSL**:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/webhookdb?useSSL=true&requireSSL=true
   ```

4. **Use connection pooling** with appropriate pool sizes for your load

5. **Set up database backups** and monitoring

6. **Use environment variables** for sensitive configuration:
   ```properties
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   spring.datasource.url=${DB_URL}
   ```

