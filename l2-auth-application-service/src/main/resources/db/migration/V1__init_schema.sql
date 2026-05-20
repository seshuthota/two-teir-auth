CREATE TABLE auth_client (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_name VARCHAR(255) NOT NULL,
    client_secret_hash VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE auth_scope (
    id BIGSERIAL PRIMARY KEY,
    scope_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE auth_client_scope (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    scope_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, scope_name)
);

CREATE TABLE auth_api_scope_mapping (
    id BIGSERIAL PRIMARY KEY,
    http_method VARCHAR(20) NOT NULL,
    api_pattern VARCHAR(255) NOT NULL,
    required_scope VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE auth_token_audit (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    token_id VARCHAR(100),
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    status VARCHAR(30),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
