-- Seed scopes
MERGE INTO auth_scope t
USING (
    SELECT 'tracking:read' AS scope_name, 'View tracking information' AS description FROM DUAL UNION ALL
    SELECT 'shipment:create', 'Create new shipments' FROM DUAL UNION ALL
    SELECT 'shipment:update', 'Update existing shipments' FROM DUAL UNION ALL
    SELECT 'status:update', 'Update status information' FROM DUAL UNION ALL
    SELECT 'invoice:read', 'View invoices' FROM DUAL
) s ON (t.scope_name = s.scope_name)
WHEN NOT MATCHED THEN
    INSERT (scope_name, description) VALUES (s.scope_name, s.description);

-- Seed client: external_partner_001 / secret: secret123
MERGE INTO auth_client t
USING (
    SELECT 'external_partner_001' AS client_id, 'External Partner 1' AS client_name,
           'HRCdKH8hv4+Lt3dvGxAwmg==:AjABsSPXKezK79vFtvfqErGtIt3TEMB7PoeOtzOBlXw=' AS client_secret_hash,
           'ACTIVE' AS status FROM DUAL
) s ON (t.client_id = s.client_id)
WHEN NOT MATCHED THEN
    INSERT (client_id, client_name, client_secret_hash, status)
    VALUES (s.client_id, s.client_name, s.client_secret_hash, s.status);

-- Map client to scopes
MERGE INTO auth_client_scope t
USING (
    SELECT 'external_partner_001' AS client_id, 'tracking:read' AS scope_name FROM DUAL UNION ALL
    SELECT 'external_partner_001', 'shipment:create' FROM DUAL
) s ON (t.client_id = s.client_id AND t.scope_name = s.scope_name)
WHEN NOT MATCHED THEN
    INSERT (client_id, scope_name) VALUES (s.client_id, s.scope_name);

-- Seed API-to-scope mappings
MERGE INTO auth_api_scope_mapping t
USING (
    SELECT 'GET' AS http_method, '/tracking/{awb}' AS api_pattern, 'tracking:read' AS required_scope, 'ACTIVE' AS status FROM DUAL UNION ALL
    SELECT 'POST', '/shipment', 'shipment:create', 'ACTIVE' FROM DUAL UNION ALL
    SELECT 'PUT', '/shipment/{id}', 'shipment:update', 'ACTIVE' FROM DUAL UNION ALL
    SELECT 'POST', '/status/update', 'status:update', 'ACTIVE' FROM DUAL UNION ALL
    SELECT 'GET', '/invoice/{id}', 'invoice:read', 'ACTIVE' FROM DUAL
) s ON (t.http_method = s.http_method AND t.api_pattern = s.api_pattern AND t.required_scope = s.required_scope)
WHEN NOT MATCHED THEN
    INSERT (http_method, api_pattern, required_scope, status)
    VALUES (s.http_method, s.api_pattern, s.required_scope, s.status);
