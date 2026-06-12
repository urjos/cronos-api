# CRONOS API

API REST para la gestión de tiempos y control de proyectos, inspirado en Clockify.
Construido con JDK 11, Javalin y MySQL 8.

## Tecnologías
- Java 11
- Maven
- MySQL 8.0+
- Javalin & HikariCP

## Configuración Local
1. Clonar el repositorio.
2. Crear un archivo `.env` basado en `.env.example`.
3. Ejecutar el script SQL ubicado en `database/init_cronos_db.sql`.
4. Construir y ejecutar mediante Maven o NetBeans 14.

## CORS para despliegue
Configura `CORS_ALLOWED_ORIGINS` en tu `.env` con los dominios permitidos, separados por comas.

Ejemplo:
`CORS_ALLOWED_ORIGINS=https://app.midominio.com,https://admin.midominio.com`

Si no se define, el API permite cualquier origen (`*`), útil para desarrollo local.
