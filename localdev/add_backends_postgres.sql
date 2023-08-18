INSERT INTO gateway_backend
(name, routing_group, backend_url, external_url, active)
VALUES
('trino-1', 'adhoc', 'http://localhost:8081', 'http://localhost:8081', true),
('trino-2', 'adhoc', 'http://localhost:8082', 'http://localhost:8082', true);
