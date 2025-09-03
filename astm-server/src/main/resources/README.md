# Environment Configuration Guide

This project uses Spring Boot profiles to manage environment-specific configurations.

## Configuration Files

### Common Configuration
- **`application.properties`** - Contains shared settings across all environments
  - Application name and basic Spring Boot configuration
  - Common instrument definitions (OrthoVision, HematologyAnalyzer)
  - Shared messaging queue names and RabbitMQ settings
  - Common logging patterns

### Environment-Specific Configurations

#### Local Development (`application-local.yml`)
- **Profile**: `local`
- **Purpose**: Local development with minimal external dependencies
- **Features**:
  - RabbitMQ messaging disabled by default
  - Debug logging for development
  - Only OrthoVision instrument enabled
  - Actuator endpoints enabled for debugging

#### Development Environment (`application-dev.yml`)
- **Profile**: `dev`
- **Purpose**: Shared development environment for integration testing
- **Features**:
  - RabbitMQ messaging enabled
  - Both instruments enabled
  - Environment variable support for credentials
  - Standard INFO logging

#### Production Environment (`application-prod.yml`)
- **Profile**: `prod`
- **Purpose**: Production deployment with high availability
- **Features**:
  - Full messaging enabled with production RabbitMQ cluster
  - Higher connection pools and timeouts
  - Conservative logging (WARN/ERROR levels)
  - Environment variables required for all credentials
  - Production-ready file logging paths

## How to Use

### 1. Setting Active Profile

#### Via Environment Variable
```bash
export SPRING_PROFILES_ACTIVE=local
# or
export SPRING_PROFILES_ACTIVE=dev
# or  
export SPRING_PROFILES_ACTIVE=prod
```

#### Via JVM Arguments
```bash
java -Dspring.profiles.active=local -jar astm-interface-service.jar
java -Dspring.profiles.active=dev -jar astm-interface-service.jar
java -Dspring.profiles.active=prod -jar astm-interface-service.jar
```

#### Via application.properties (default)
The `application.properties` file sets `local` as the default profile.

### 2. Maven Profiles (Optional)
You can also create Maven profiles to automatically set Spring profiles:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 3. IDE Configuration
In your IDE, set the active profile in the run configuration:
- **VM Options**: `-Dspring.profiles.active=local`
- **Environment Variables**: `SPRING_PROFILES_ACTIVE=local`

## Environment Variables

### Development Environment
```bash
RABBITMQ_USERNAME=dev_user
RABBITMQ_PASSWORD=dev_password
```

### Production Environment (Required)
```bash
RABBITMQ_HOST=prod-rabbitmq.internal
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=prod_user
RABBITMQ_PASSWORD=secure_password
SERVER_PORT=8080
DATABASE_URL=jdbc:postgresql://prod-db.internal:5432/lis_prod
DB_USERNAME=lis_prod_user
DB_PASSWORD=secure_db_password
```

## Configuration Priority

Spring Boot loads configurations in this order (highest to lowest priority):
1. Command line arguments
2. Environment variables
3. `application-{profile}.yml`
4. `application.properties`

## Legacy Configuration
- **`application-legacy.yml`** - Original monolithic configuration kept for reference

## Troubleshooting

### Profile Not Loading
- Check that the profile name matches exactly (case-sensitive)
- Verify the file name follows the pattern `application-{profile}.yml`
- Check the logs for profile activation messages

### Missing Properties
- Environment-specific properties override common properties
- Use `${VARIABLE_NAME:default_value}` syntax for optional environment variables
- Use `${VARIABLE_NAME}` syntax for required environment variables

### RabbitMQ Connection Issues
- Local: Ensure RabbitMQ is running locally or set `lis.messaging.enabled=false`
- Dev/Prod: Verify environment variables are set correctly
- Check network connectivity to RabbitMQ hosts
