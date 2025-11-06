# ETX API (Java SpringBoot)

This directory contains a Java SpringBoot-based ETX partner API. This API exposes authenticated endpoints for authentication (token generation), configuration, registration, and connection.

## Running the application

The following explains how to run the java partner api locally. If you would like to run this application through docker instead, simply run:

```
cd ../
docker compose up --build -d
```

### Install and run dependencies

**NOTE** This application requires Java 21

To install packages through gradle:

```
./gradlew build
```

This application depends on keycloak - this can be run through the [api/docker-compose.yml](../docker-compose.yml), with the following command:

```
cd ../
docker compose up --build -d imp_keycloak
```

### Run the application

#### VS Code

Run the "Java Partner Api" task. This will use your environment variables from the /api/.env

#### Command Line

Create an application-dev.yml

Copy the [application.yml](./src/main/resources/application.yml) to ./src/main/resources/application-dev.yml. Then, replace placeholders with values from your .env

Run the application in the dev profile:

```
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Run Unit Tests

```
./gradlew clean test
```

## Application Structure

This partner api has 3 rest controllers:

1. [Registration/Connection](./src/main/java/usdot/v2x/app/api/etx/registration/RegistrationRestController.java)
   - This rest controller hosts ETX endpoints for registration and connection
2. [Configuration](./src/main/java/usdot/v2x/app/api/etx/configuration/ConfigurationRestController.java)
   - This rest controller hosts ETX geofence endpoints for managing TIMs
3. [Authentication](./src/main/java/usdot/v2x/app/api/keycloak/KeycloakRestController.java)
   - This rest controller hosts api authentication endpoints - mainly a token login endpoint, enabling keycloak token generation
4. [Decode](./src/main/java/usdot/v2x/app/api/decode/DecodeRestController.java)
   - This rest controller hosts ASN.1 decode endpoints for decoding messages

## Authentication

Endpoints + Allowed Roles:

- /auth/token
  - _none_
- /api/v2/registration POST
  - ROLE_ADMIN, ROLE_DEPOSITOR, ROLE_USER
- /api/v2/registration PUT
  - ROLE_ADMIN, ROLE_DEPOSITOR, ROLE_USER
- /api/v2/connection POST
  - ROLE_ADMIN, ROLE_DEPOSITOR, ROLE_USER
- /api/v2/registration-connection POST
  - ROLE_ADMIN, ROLE_DEPOSITOR, ROLE_USER
- /api/v2/configurations/geofence/ids GET
  - ROLE_ADMIN, ROLE_DEPOSITOR
- /api/v2/configurations/geofence/ids GET
  - ROLE_ADMIN, ROLE_DEPOSITOR
- /api/v2/configurations/geofence GET
  - ROLE_ADMIN, ROLE_DEPOSITOR
- /api/v2/configurations/geofence POST
  - ROLE_ADMIN, ROLE_DEPOSITOR
- /api/v2/configurations/geofence PUT
  - ROLE_ADMIN, ROLE_DEPOSITOR
- /api/v2/configurations/geofence DELETE
  - ROLE_ADMIN, ROLE_DEPOSITOR
