-- Seed data for H2 in-memory profile
-- Runs after JPA ddl-auto creates the schema
-- Only executes for embedded databases (H2), ignored for Oracle

INSERT INTO auth_scope (scope_name, description) VALUES
('tracking:read', 'View tracking information'),
('shipment:create', 'Create new shipments'),
('shipment:update', 'Update existing shipments'),
('status:update', 'Update status information'),
('invoice:read', 'View invoices');

INSERT INTO auth_client (client_id, client_name, client_secret_hash, status, created_at, updated_at) VALUES
('external_partner_001', 'External Partner 1',
 'HRCdKH8hv4+Lt3dvGxAwmg==:AjABsSPXKezK79vFtvfqErGtIt3TEMB7PoeOtzOBlXw=',
 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO auth_client_scope (client_id, scope_name, created_at) VALUES
('external_partner_001', 'tracking:read', CURRENT_TIMESTAMP),
('external_partner_001', 'shipment:create', CURRENT_TIMESTAMP);

INSERT INTO auth_api_scope_mapping (http_method, api_pattern, required_scope, status, created_at, updated_at) VALUES
('GET', '/tracking/{awb}', 'tracking:read', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('POST', '/shipment', 'shipment:create', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PUT', '/shipment/{id}', 'shipment:update', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('POST', '/status/update', 'status:update', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GET', '/invoice/{id}', 'invoice:read', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
