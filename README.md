# Lightweight Task Scheduler

A lightweight, standalone task scheduling system built with Java and Spring Boot. It allows users to define, manage, and monitor scheduled tasks through a web UI and REST API. The scheduler supports various advanced features including distributed locking, complex scheduling rules, notifications, and workflow orchestration.

## Features

*   **Dynamic Task Scheduling**: Configure tasks with CRON expressions.
*   **Multiple Task Types**:
    *   **Bean Tasks**: Execute methods on specified Spring beans.
    *   **HTTP Tasks**: Make HTTP requests to specified URLs with configurable method, headers, body, and timeouts.
    *   **Workflow Tasks**: Orchestrate a sequence of other tasks (primarily bean tasks). (Type 10)
    *   **Shell Tasks**: Execute shell scripts. Parameters (script content/path, arguments, working directory) are stored in `beanParameters` as a JSON string. (Type 4)
*   **Execution Modes**:
    *   **BROADCAST**: Task runs on all scheduler instances.
    *   **CLUSTER**: Task runs on only one instance at a time using a distributed lock (lock name automatically derived from task ID, lease time is internally managed).
*   **REST API**: Comprehensive API for managing tasks, users, logs, and calendars.
*   **Web UI**: User-friendly interface for:
    *   User authentication (JWT-based).
    *   Task configuration and management (CRUD, trigger, enable/disable), including specific forms for Bean, HTTP, and Workflow tasks.
    *   Viewing task execution logs with basic filtering and client-side pagination.
    *   Managing calendars for task exclusion.
    *   User administration.
*   **Advanced Task Features**:
    *   **Date/Time Exclusions**: Define start/end dates for tasks, specific calendar groups for non-working days, and daily time ranges for exclusion.
    *   **Execution Timeout**: Configure maximum execution time for tasks (applies to Bean, HTTP, and Shell tasks).
    *   **Notifications**: Send webhook notifications on task success or failure to configured user webhook addresses.
*   **Workflow Support**:
    *   Define workflows as a series of nodes (bean tasks) and edges.
    *   **Global Parameters**: Define parameters at the workflow level.
    *   **Data Passing**: Use `${variable}` templating in node parameters to access global parameters or status from previous nodes (e.g., `${nodeA_status}`).
    *   **Conditional Logic**: Edges can have expressions (e.g., `"${nodeA_status} == 'SUCCESS'"`) to control flow based on the outcome of previous nodes. Edge priority is supported.
*   **Global Exception Handling**: Centralized exception handling for REST API calls, providing consistent JSON error responses.
*   **Unit Tests**: Core services and utilities are covered by unit tests (JUnit 5, Mockito).
*   **Localized Frontend Assets**: All frontend JavaScript and CSS libraries (Bootstrap, jQuery, Popper.js) are served locally.
*   **MDC Logging**: Enhanced logging with `execute_no` (task execution log ID) in MDC for better traceability, configured via `logback-spring.xml`.
*   **Performance**: Guava caching implemented for frequently accessed User and Task Calendar data to reduce database load.
* **Debug Utilities**: Built-in debugging panel accessible via Ctrl+Shift+D for troubleshooting in development and production environments.

## Technologies Used

*   **Backend**:
    *   Java 8
    *   Spring Boot (Web, JDBC, Scheduling)
    *   Fastjson (for JSON processing, replacing default Jackson)
    *   Guava (for in-memory caching in DAOs)
    *   Maven (Build Tool)
    *   Logback (for logging, with MDC)
*   **Frontend**:
    *   HTML5
    *   CSS3 (Bootstrap 4, custom styles)
    *   JavaScript (ES6, jQuery)
    *   Optimized workflow graph implementation (tasks-workflow-fixed.js)
    *   Lazy-loading of form components to improve page load performance

## Recent Updates

### Frontend Optimization & Performance Improvements - June 2025

* **Code Cleanup**: Removed redundant JavaScript files and consolidated frontend code.
* **Workflow Graph**: Improved the workflow graph implementation with enhanced stability and error handling.
* **Performance Enhancements**:
  * Added smart cache expiration for API data
  * Optimized DOM operations in workflow rendering
  * Implemented lazy-loading for form components
* **Debugging Tools**:
  * Added debug mode toggle (localStorage based)
  * Implemented in-browser debugging panel (Ctrl+Shift+D)
  * Enhanced error handling with specific error messages
* **Key Files**:
  * `tasks-workflow-fixed.js` - Main implementation of the workflow graph functionality
  * `lazy-loader.js` - Optimized loading of UI components
  * `debug-utils.js` - Debugging utilities for development and production
  * `collapsible-sections.js` - Improved UI for form sections

### Project Status
* All core functionality is working properly after code cleanup
* Frontend code has been optimized for better performance and maintainability
* Workflow graph implementation has been stabilized with improved error handling
* Memory usage optimized with smarter caching strategies

*   **Database**:
    *   Designed for MySQL.
    *   H2 Database for embedded testing/development (default).
*   **Authentication**:
    *   JSON Web Tokens (JWT)
*   **Testing**:
    *   JUnit 5
    *   Mockito

## Prerequisites

*   JDK 1.8 or higher (e.g., OpenJDK 1.8, Oracle JDK 8).
*   Maven 3.2 or higher.
*   MySQL server (optional, if not using the default H2 in-memory database).

## Database Setup

The database schema is defined in `src/main/resources/schema.sql`. This file contains `CREATE TABLE` statements for all necessary tables and some example data.

**For H2 (Default):**
The application uses an H2 in-memory database by default. The schema will be created and initialized automatically on startup based on `schema.sql` because Spring Boot JDBC autoconfiguration will pick it up.

**For MySQL:**
1.  Create a database in MySQL (e.g., `task_scheduler_db`).
    ```sql
    CREATE DATABASE task_scheduler_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    ```
2.  Connect to your MySQL database using a client (e.g., `mysql` command line, MySQL Workbench).
3.  Execute the contents of `src/main/resources/schema.sql` against the created database to set up the tables.
4.  Update the `application.properties` file with your MySQL connection details (see Configuration section).

## Configuration (`application.properties`)

The main configuration file is located at `src/main/resources/application.properties`. Comments within the file explain each property.

Key properties to configure:

*   **Database Connection (for MySQL)**:
    *   `spring.datasource.url`
    *   `spring.datasource.username`
    *   `spring.datasource.password`
    *   `spring.datasource.driverClassName`
    (Uncomment and update these if using MySQL. By default, H2 is used.)

*   **JWT Settings**:
    *   `jwt.secret`: **Important!** Change to a strong, unique value for production (min 32 bytes).
    *   `jwt.expirationMs`: Token expiration time (default is 1 hour).

*   **Scheduler Instance ID (Optional)**:
    *   `scheduler.instance.id`: Useful in a clustered environment. Defaults to a random UUID.

*   **Distributed Lock Retry Settings**:
    *   `scheduler.lock.retry.maxAttempts`: Default 3.
    *   `scheduler.lock.retry.delayMs`: Default 1000ms.

*   **Task Scheduling Pool Size**:
    *   `spring.task.scheduling.pool.size`: Default 10.

## Building the Project

1.  Navigate to the project root directory.
2.  Run the Maven command:
    ```bash
    mvn clean package
    ```
    This will compile the code, run tests, and create a JAR file in the `target/` directory (e.g., `taskscheduler-0.0.1-SNAPSHOT.jar`).

## Running the Application

1.  After building the project, navigate to the `target/` directory.
2.  Run the application using:
    ```bash
    java -jar taskscheduler-0.0.1-SNAPSHOT.jar
    ```
3.  The application will start, and by default, the UI is accessible at `http://localhost:8080`.
    (The port can be changed in `application.properties` using `server.port=xxxx`).
    Default login: `admin` / `password`.

## API Endpoints Overview

The application exposes RESTful APIs for managing its resources. All API endpoints are prefixed with `/embed-api`. Authentication is required for most endpoints using a JWT Bearer token.

*   **Authentication (`/embed-api/auth`)**
    *   `POST /login`: Authenticates a user, returns JWT.
*   **Task Configurations (`/embed-api/tasks`)**
    *   `GET /`: List all task configurations.
    *   `POST /`: Create a new task configuration.
    *   `GET /{id}`: Get a specific task configuration.
    *   `PUT /{id}`: Update a task configuration.
    *   `DELETE /{id}`: Delete a task configuration.
    *   `POST /{id}/trigger`: Manually trigger a task.
    *   `POST /{id}/enable`: Enable a task.
    *   `POST /{id}/disable`: Disable a task.
*   **Task Execution Logs (`/embed-api/logs`)**
    *   `GET /`: List all task execution logs (supports pagination via query params `page` & `size`).
    *   `GET /task/{taskId}`: List logs for a specific task (supports pagination).
    *   `GET /{id}`: Get a specific log entry.
*   **Users (`/embed-api/users`)**
    *   `GET /`: List all users.
    *   `POST /`: Create a new user.
    *   `GET /{id}`: Get a specific user.
    *   `PUT /{id}`: Update a user.
    *   `DELETE /{id}`: Delete a user.
*   **Calendars (`/embed-api/calendars`)**
    *   `GET /`: List all calendars and their day entries.
    *   `POST /`: Create a new calendar.
    *   `GET /{id}`: Get a specific calendar and its days.
    *   `GET /name/{calendarName}`: Get a calendar by its name.
    *   `DELETE /{id}`: Delete a calendar and its days.
    *   `POST /{calendarId}/days`: Add a day entry to a calendar.
    *   `GET /{calendarId}/days`: List days for a specific calendar.
    *   `DELETE /{calendarId}/days/{dayId}`: Remove a day entry from a calendar.

## Core Concepts

### Task Configuration (`TaskConfig`)
This is the central entity for defining a schedulable job.
*   **Task Types** (`taskType` field):
    *   `0`: **Bean Task**: Executes `methodName` on a Spring `beanName`. Parameters via `beanParameters` (JSON string).
    *   `1`: **(Legacy/Unused)**
    *   `2`: **HTTP Task**: Makes an HTTP request. Parameters (URL, method, headers, body, timeouts) are stored in `beanParameters` as a JSON string matching `HttpTaskParameters` DTO.
    *   `4`: **Shell Task**: Executes a shell script. Parameters (script content/path, arguments, working directory) are stored in `beanParameters` as a JSON string matching `ShellTaskParameters` DTO.
    *   `10`: **Workflow Task**: Orchestrates a series of other tasks (primarily bean tasks).
*   **Key Scheduling Fields**: `cronExpression`, `isActive`, `startDate`, `endDate`, `taskCalendarGroup`, `taskExcludeTimes`.
*   **Execution Control**:
    *   `executionMode`: Defines behavior in a cluster.
        *   `BROADCAST` (default): Task runs on all instances.
        *   `CLUSTER`: Task runs on only one instance using a distributed lock (lock name is automatically derived, e.g., `task_lock_id_{taskId}`). Lease duration is internally managed (e.g., 60 seconds).
    *   `executeTimeoutSeconds`.
*   **Notifications**: `notifySuccessUserIds`, `notifyFailedUserIds` (comma-separated User IDs). Configuration is per-user.

### Bean Tasks
1.  Define a Spring component (e.g., `@Component("yourBeanName")`).
2.  Implement a public method in this bean.
3.  Configure a `TaskConfig` with `taskType = 0`, `beanName`, `methodName`, and optionally `beanParameters` (JSON).

### HTTP Tasks
1.  Configure a `TaskConfig` with `taskType = 2`.
2.  Store HTTP request details as a JSON string in the `beanParameters` field. This JSON must match the structure of the `HttpTaskParameters` DTO (`url`, `method`, `headers`, `body`, `connectTimeout`, `readTimeout`).
    *   Example: `{"url":"https://embed-api.example.com/data", "method":"POST", "headers":{"Content-Type":"application/json"}, "body":"{\"key\":\"value\"}", "connectTimeout":5000, "readTimeout":10000}`

### Workflow Tasks
*   `taskType = 10`.
*   `globalParametersJson`: JSON map for global parameters, accessible via templating (e.g., `${global_param}`).
*   `workflowNodesJson`: JSON array of `WorkflowNode` objects. Each node specifies a `taskConfigId` (must be a Bean task) and can override parameters using templating.
*   `workflowEdgesJson`: JSON array of `WorkflowEdge` objects. Each edge defines `fromNodeId`, `toNodeId`, `priority`, and an `expression` (e.g., `"${nodeA_status} == 'SUCCESS'"`) for conditional transitions. Context for expressions includes global parameters and `nodeId_status` from previous nodes.

### Distributed Locks
*   For `CLUSTER` mode, a distributed lock is automatically managed. The lock name is derived from the task ID.
*   The lock lease duration is internally managed by the `DistributedLockService` (e.g., default 60 seconds).
*   Retry mechanism for lock acquisition is configurable via `application.properties`.

### Notifications (Extensible System)
*   Notifications are managed via a channel-based system (`NotificationChannel` interface).
*   **Webhook Channel**: This is the primary implemented channel.
    *   Users configure their webhook endpoint(s) in the `webhookAddress` field of their user profile. This field can be a single URL string or a JSON array of URL strings (e.g., `["http://url1.com", "http://url2.com"]`).
    *   Tasks specify `notifySuccessUserIds` / `notifyFailedUserIds` (comma-separated User IDs).
    *   A JSON payload (generated by Fastjson) with execution details is POSTed to the configured webhook(s).
*   **Future Extensibility**:
    *   The `TaskUser` entity has a `notificationPreferencesJson` field intended for future use, allowing users to specify preferences for different channels (e.g., email, Slack) and their respective details (e.g., `{"EMAIL": {"emailAddress": "user@example.com", "enabled": true}}`).
    *   Developers can add new notification methods by implementing the `NotificationChannel` interface and registering it as a Spring bean. `NotificationService` will automatically pick it up.

### Caching
*   **DAO Caching**: To enhance performance, Guava caches are utilized within `TaskUserDaoImpl` and `TaskCalendarDaoImpl`.
    *   User data (by ID and username) and Task Calendar data (calendars by ID/name, days by calendar ID, specific day by ID/date) are cached.
    *   Caches are configured with maximum sizes and time-based expiration (e.g., 1 hour for users, 6 hours for calendars).
    *   Cache invalidation logic is implemented in DAO methods that modify data (e.g., save, update, delete) to maintain data consistency. Logging for cache operations (hits, misses, puts, invalidations) is included at DEBUG level.

## UI Guide

*   **Login (`/login.html`)**: Default `admin`/`password`.
*   **Tasks (`/tasks.html`)**: CRUD operations.
    *   For "Bean Task", specify bean and method names, and JSON parameters in the "Bean Parameters" field.
    *   For "HTTP Task", use the specific fields provided; these are consolidated into `beanParameters` (JSON) on the backend.
    *   For "Shell Task", use the specific fields provided; these are consolidated into `beanParameters` (JSON) on the backend.
    *   For "Workflow Task", define nodes, edges, and global parameters as JSON in their respective textareas.
    *   "Execution Mode" dropdown allows selecting `BROADCAST` or `CLUSTER`.
*   **Logs (`/logs.html`)**: View execution history.
*   **Calendars (`/calendars.html`)**: Manage calendars and non-working days.
*   **Users (`/users.html`)**: Manage users and their webhook addresses.
*   **Logout**: Navbar link.
*   **Frontend Assets**: All JavaScript libraries (jQuery, Popper, Bootstrap) and CSS (Bootstrap) are served locally from the application.

## Troubleshooting

*   **Database**: Ensure schema is up-to-date. Verify connection properties.
*   **JWT/Auth**: Check `jwt.secret`. Re-login if 401 errors.
*   **Task Not Running**: Check `isActive`, cron, logs, exclusion rules, locks.
*   **Workflow Issues**: Validate the JSON for nodes/edges/globals. Ensure `taskConfigId` in nodes are valid Bean tasks. Check expressions and context variables.
*   **HTTP Task Issues**: Ensure `beanParameters` contains valid `HttpTaskParameters` JSON (as handled by the UI). Check URL, method, headers, body. Verify network connectivity from the server.
*   **Logging**: Application logs (console output or configured log files) provide detailed information. The pattern includes `[exec_no:%X{execute_no}]` for tracing specific task runs. Increase log level for `com.example.taskscheduler` to `DEBUG` in `application.properties` for more verbose output.

## Code Structure

*   `com.example.taskscheduler`
    *   `config`: Spring configurations (`WebConfig`, `GlobalExceptionHandler`).
    *   `controller`: REST APIs.
    *   `dao`: Database access.
    *   `dto`: Data Transfer Objects.
        *   `taskparams`: DTOs for specific task type parameters (e.g., `HttpTaskParameters`).
        *   `workflow`: DTOs for workflow definitions (`WorkflowNode`, `WorkflowEdge`).
    *   `entity`: Database entities.
    *   `scheduler`: Core scheduling logic (`CoreSchedulerService`).
    *   `service`: Business services (e.g., `HttpTaskExecutor`, `BeanTaskExecutor`, `WorkflowExecutionService`).
    *   `util`: Utility classes.
*   `src/main/resources`
    *   `application.properties`: Configuration.
    *   `logback-spring.xml`: Logging configuration (for MDC).
    *   `schema.sql`: Database schema.
    *   `static`: Frontend resources (HTML, CSS, JS, localized libraries in `libs/`).
*   `src/test/java`: Unit tests.

This structure promotes separation of concerns and maintainability.
