# ColligendisServerReact

A modern reactive backend server built with Spring Boot WebFlux, Neo4j, and JWT authentication for secure communication with Dart frontend applications.

## Features

- **Spring Boot 3.4.0** with **Java 21**
- **WebFlux** for reactive, non-blocking API
- **Neo4j 5.16.0** reactive database
- **JWT Authentication** with token and refresh token support
- **Spring Security** with WebFlux
- **HTTPS/SSL** support with self-signed certificate
- **CORS** configured for Dart frontend
- **Comprehensive Logging** with SLF4J and Logback
- **Method Execution Time Measurement** using AOP
- **Performance Monitoring** with configurable thresholds

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.4.0
- **Spring WebFlux**: Reactive web framework
- **Spring Security**: JWT-based authentication
- **Neo4j**: Reactive graph database
- **JWT**: io.jsonwebtoken 0.12.5
- **Lombok**: Code generation
- **AOP**: Aspect-Oriented Programming for cross-cutting concerns

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Neo4j 5.16.0 or higher running locally
- Port 8443 available for HTTPS

## Neo4j Setup

1. Install Neo4j from https://neo4j.com/download/
2. Start Neo4j server:
   ```bash
   neo4j start
   ```
3. Access Neo4j Browser at http://localhost:7474
4. Set up credentials (default: neo4j/password)
5. Update `src/main/resources/application.yml` with your credentials

## Building the Project

```bash
cd ColligendisServerReact
mvn clean install
```

## Running the Application

```bash
mvn spring-boot:run
```

The server will start on `https://localhost:8443`

## Configuration

### Application Configuration

Edit `src/main/resources/application.yml` to configure:

- **Server Port**: Default 8443 (HTTPS)
- **Neo4j Connection**: URI, username, password
- **JWT Settings**: Secret key, expiration times
- **Logging Levels**: DEBUG, INFO, WARN, ERROR
- **CORS**: Allowed origins for Dart frontend
- **Performance Monitoring**: Execution time thresholds

### SSL Certificate

A self-signed certificate is included for development. For production:

```bash
keytool -genkeypair -alias colligendis -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore src/main/resources/keystore.p12 \
  -validity 3650 -storepass YOUR_PASSWORD
```

Update `application.yml` with your keystore password.

## API Endpoints

### Authentication Endpoints

- **POST** `/api/auth/login` - User login
  ```json
  {
    "username": "user",
    "password": "password"
  }
  ```

- **POST** `/api/auth/refresh` - Refresh access token
  ```json
  {
    "refreshToken": "your-refresh-token"
  }
  ```

- **GET** `/api/auth/health` - Health check

### User Endpoints (Authenticated)

- **POST** `/api/users` - Create user
- **GET** `/api/users` - Get all users
- **GET** `/api/users/{id}` - Get user by ID
- **GET** `/api/users/username/{username}` - Get user by username
- **PUT** `/api/users/{id}` - Update user
- **DELETE** `/api/users/{id}` - Delete user

### Product Endpoints (Authenticated)

- **POST** `/api/products` - Create product
- **GET** `/api/products` - Get all products
- **GET** `/api/products/{id}` - Get product by ID
- **GET** `/api/products/category/{category}` - Get products by category
- **GET** `/api/products/search?term={searchTerm}` - Search products
- **GET** `/api/products/price-range?minPrice={min}&maxPrice={max}` - Filter by price
- **PUT** `/api/products/{id}` - Update product
- **DELETE** `/api/products/{id}` - Delete product

## Authentication Flow

1. **Login**: POST to `/api/auth/login` with credentials
2. **Receive Tokens**: Get access token and refresh token
3. **Use Access Token**: Add `Authorization: Bearer <token>` header to requests
4. **Refresh Token**: When access token expires, use refresh token to get new tokens

## Logging

The application uses comprehensive logging:

- **Controller Layer**: Request/response logging
- **Service Layer**: Business logic and method execution time
- **Repository Layer**: Database operations
- **Security Layer**: Authentication and authorization events

Logs are written to:
- Console: Real-time monitoring
- File: `logs/application.log`

## Performance Monitoring

Methods annotated with `@LogExecutionTime` are automatically monitored:

```java
@LogExecutionTime
public Mono<User> createUser(User user) {
    // Method implementation
}
```

Execution times exceeding the threshold (default 1000ms) trigger warnings.

## Examples

### Using Logging in Your Code

```java
@Slf4j
@Service
public class MyService {
    public void myMethod() {
        log.debug("Debug message");
        log.info("Info message");
        log.warn("Warning message");
        log.error("Error message");
    }
}
```

### Using Execution Time Measurement

```java
@Service
public class MyService {
    @LogExecutionTime
    public Mono<Result> expensiveOperation() {
        // This method's execution time will be logged
        return repository.complexQuery();
    }
}
```

## Security Considerations

⚠️ **For Production Use:**

1. Replace the JWT secret in `application.yml` with a strong random key
2. Use a proper SSL certificate from a trusted CA
3. Implement password encoding (BCrypt is configured)
4. Enable rate limiting for authentication endpoints
5. Configure Neo4j with strong credentials
6. Enable HTTPS only (disable HTTP)
7. Implement proper error handling without exposing sensitive information

## CORS Configuration

The server is configured to accept requests from Dart frontend applications running on:
- `http://localhost:3000`
- `https://localhost:3000`

Modify `cors.allowed-origins` in `application.yml` to add more origins.

## Testing

Run tests with:

```bash
mvn test
```

## Project Structure

```
ColligendisServerReact/
├── src/
│   ├── main/
│   │   ├── java/com/colligendis/server/
│   │   │   ├── aspect/          # AOP aspects for logging and timing
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── model/           # Neo4j entities
│   │   │   ├── repository/      # Reactive repositories
│   │   │   ├── security/        # Security and JWT components
│   │   │   ├── service/         # Business logic services
│   │   │   └── ColligendisServerApplication.java
│   │   └── resources/
│   │       ├── application.yml  # Application configuration
│   │       └── keystore.p12     # SSL certificate
│   └── test/                    # Test classes
├── logs/                        # Application logs
├── pom.xml                      # Maven dependencies
└── README.md                    # This file
```

## Troubleshooting

### Neo4j Connection Issues

- Ensure Neo4j is running: `neo4j status`
- Check credentials in `application.yml`
- Verify Neo4j is accessible at `bolt://localhost:7687`

### SSL Certificate Issues

- For development, accept the self-signed certificate warning
- For production, use a proper CA-signed certificate

### Port Already in Use

If port 8443 is in use, change it in `application.yml`:

```yaml
server:
  port: 8444
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License.

## Contact

For questions or support, please contact the development team.
