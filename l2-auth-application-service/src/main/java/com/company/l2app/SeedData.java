package com.company.l2app;

import com.company.l2app.entity.AuthApiScopeMapping;
import com.company.l2app.entity.AuthClient;
import com.company.l2app.entity.AuthClientScope;
import com.company.l2app.entity.AuthScope;
import com.company.l2app.repository.AuthApiScopeMappingRepository;
import com.company.l2app.repository.AuthClientRepository;
import com.company.l2app.repository.AuthClientScopeRepository;
import com.company.l2app.repository.AuthScopeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("default")
public class SeedData implements CommandLineRunner {

    private final AuthScopeRepository scopeRepository;
    private final AuthClientRepository clientRepository;
    private final AuthClientScopeRepository clientScopeRepository;
    private final AuthApiScopeMappingRepository apiScopeMappingRepository;

    public SeedData(AuthScopeRepository scopeRepository,
                     AuthClientRepository clientRepository,
                     AuthClientScopeRepository clientScopeRepository,
                     AuthApiScopeMappingRepository apiScopeMappingRepository) {
        this.scopeRepository = scopeRepository;
        this.clientRepository = clientRepository;
        this.clientScopeRepository = clientScopeRepository;
        this.apiScopeMappingRepository = apiScopeMappingRepository;
    }

    @Override
    public void run(String... args) {
        if (scopeRepository.count() > 0) return;

        var tracking = scopeRepository.save(new AuthScope("tracking:read", "View tracking information"));
        var shipmentCreate = scopeRepository.save(new AuthScope("shipment:create", "Create new shipments"));
        scopeRepository.save(new AuthScope("shipment:update", "Update existing shipments"));
        scopeRepository.save(new AuthScope("status:update", "Update status information"));
        scopeRepository.save(new AuthScope("invoice:read", "View invoices"));

        var client = clientRepository.save(new AuthClient(
                "external_partner_001", "External Partner 1",
                "HRCdKH8hv4+Lt3dvGxAwmg==:AjABsSPXKezK79vFtvfqErGtIt3TEMB7PoeOtzOBlXw=",
                "ACTIVE"));

        clientScopeRepository.save(new AuthClientScope(client.getClientId(), tracking.getScopeName()));
        clientScopeRepository.save(new AuthClientScope(client.getClientId(), shipmentCreate.getScopeName()));

        saveMapping("GET", "/tracking/{awb}", "tracking:read");
        saveMapping("POST", "/shipment", "shipment:create");
        saveMapping("PUT", "/shipment/{id}", "shipment:update");
        saveMapping("POST", "/status/update", "status:update");
        saveMapping("GET", "/invoice/{id}", "invoice:read");
    }

    private void saveMapping(String method, String pattern, String scope) {
        var mapping = new AuthApiScopeMapping();
        mapping.setHttpMethod(method);
        mapping.setApiPattern(pattern);
        mapping.setRequiredScope(scope);
        mapping.setStatus("ACTIVE");
        apiScopeMappingRepository.save(mapping);
    }
}
