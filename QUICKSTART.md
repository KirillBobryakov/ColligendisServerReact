# Quick Start Guide

## 1. Start Neo4j Database

Using Docker (recommended):
```bash
docker-compose up -d
```

Or install and start Neo4j manually from https://neo4j.com/download/

## 2. Build the Project

```bash
mvn clean install
```

## 3. Run the Application

```bash
mvn spring-boot:run
```

The server will start at: **https://localhost:8443**

## 4. Test the API

### Health Check
```bash
curl -k https://localhost:8443/api/auth/health
```

### Create a User
```bash
curl -k -X POST https://localhost:8443/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }'
```

### Login
```bash
curl -k -X POST https://localhost:8443/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

Save the returned `token` and `refreshToken` from the response.

### Get All Users (Authenticated)
```bash
curl -k https://localhost:8443/api/users \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Create a Product (Authenticated)
```bash
curl -k -X POST https://localhost:8443/api/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "name": "Sample Product",
    "description": "A test product",
    "price": 99.99,
    "quantity": 10,
    "category": "Electronics"
  }'
```

### Get All Products
```bash
curl -k https://localhost:8443/api/products \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Search Products
```bash
curl -k "https://localhost:8443/api/products/search?term=Sample" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Refresh Token
```bash
curl -k -X POST https://localhost:8443/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
  }'
```

## 5. View Logs

Check the console output or:
```bash
tail -f logs/application.log
```

## 6. Access Neo4j Browser

Open http://localhost:7474 in your browser
- Username: neo4j
- Password: password

## Notes

- The `-k` flag in curl commands is used to skip SSL certificate verification for the self-signed certificate
- For Dart frontend integration, use the appropriate HTTP client with SSL certificate validation configured
- All authenticated endpoints require the `Authorization: Bearer <token>` header

## Stopping the Application

- Press `Ctrl+C` to stop the Spring Boot application
- Stop Neo4j: `docker-compose down` (if using Docker)

## Troubleshooting

If port 8443 is already in use, modify the port in `src/main/resources/application.yml`:

```yaml
server:
  port: 8444
```
