-- SafeLab Microservices Database Initialization
-- Creates one schema per bounded context

CREATE DATABASE IF NOT EXISTS safelab_shared;
CREATE DATABASE IF NOT EXISTS safelab_telemetry;
CREATE DATABASE IF NOT EXISTS safelab_alerts;
CREATE DATABASE IF NOT EXISTS safelab_labs;
CREATE DATABASE IF NOT EXISTS safelab_iam;
CREATE DATABASE IF NOT EXISTS safelab_compliance;
CREATE DATABASE IF NOT EXISTS safelab_automation;
CREATE DATABASE IF NOT EXISTS safelab_history;

-- Grant privileges
GRANT ALL PRIVILEGES ON safelab_shared.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_telemetry.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_alerts.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_labs.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_iam.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_compliance.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_automation.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON safelab_history.* TO 'root'@'%';

FLUSH PRIVILEGES;
