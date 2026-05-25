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
           'actt0d1shqO9VQGDy/LJKg==:XS+XDeVWLYGY5W7IqhLFcKqYCw/mcYJd1pbfj4wDRTM=' AS client_secret_hash,
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


