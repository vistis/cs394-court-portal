# court-portal

The internal court for the Court Management System. Used for by Greffier users. It is the main panel of the system, as the CMS was scoped to be an internal management system only.

## Project Properties

- Project: `maven`
- Language: `java`
- Spring Boot: `4.1.0`
- Group: `kh.edu.paragoniu`
- Artifact: `court-portal`
- Packaging: `jar`
- Configuration: `yaml`
- Java: `25`

### Dependencies

- `court-shared`
- Spring Web (w/ Jetty)
- Validation
- Spring Security
- Thymeleaf
- PostgreSQL Driver
- Lombok
- Spring Boot DevTools
- Spring Session for Spring Data Redis
- Sping dotenv (by `me.paulschwarz`, version `5.1.0`)

> And additionally inherited dependencies from `court-shared`.

## `court-shared` Library

This project implements the `court-shared` library. Please refer to the README of that project for more information on what importing it does for this project.
