-- Seed scopes
INSERT INTO auth_scope (scope_name, description) VALUES
('tracking:read', 'View tracking information'),
('shipment:create', 'Create new shipments'),
('shipment:update', 'Update existing shipments'),
('status:update', 'Update status information'),
('invoice:read', 'View invoices')
ON CONFLICT (scope_name) DO NOTHING;

-- Seed client: external_partner_001 / secret: secret123
INSERT INTO auth_client (client_id, client_name, client_secret_hash, status) VALUES
('external_partner_001', 'External Partner 1',
 'HRCdKH8hv4+Lt3dvGxAwmg==:AjABsSPXKezK79vFtvfqErGtIt3TEMB7PoeOtzOBlXw=',
 'ACTIVE')
ON CONFLICT (client_id) DO NOTHING;

-- Map client to scopes
INSERT INTO auth_client_scope (client_id, scope_name) VALUES
('external_partner_001', 'tracking:read'),
('external_partner_001', 'shipment:create')
ON CONFLICT (client_id, scope_name) DO NOTHING;

-- Seed API-to-scope mappings
INSERT INTO auth_api_scope_mapping (http_method, api_pattern, required_scope, status) VALUES
('GET', '/tracking/{awb}', 'tracking:read', 'ACTIVE'),
('POST', '/shipment', 'shipment:create', 'ACTIVE'),
('PUT', '/shipment/{id}', 'shipment:update', 'ACTIVE'),
('POST', '/status/update', 'status:update', 'ACTIVE'),
('GET', '/invoice/{id}', 'invoice:read', 'ACTIVE')
ON CONFLICT DO NOTHING;
