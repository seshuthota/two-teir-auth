package com.company.l2app.service;

import com.company.l2app.entity.AuthApiScopeMapping;
import com.company.l2app.repository.AuthApiScopeMappingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PermissionService {

    private final AuthApiScopeMappingRepository apiScopeMappingRepository;

    public PermissionService(AuthApiScopeMappingRepository apiScopeMappingRepository) {
        this.apiScopeMappingRepository = apiScopeMappingRepository;
    }

    public boolean hasRequiredScope(String httpMethod, String apiPath, String tokenScope) {
        if (tokenScope == null || tokenScope.isBlank()) return false;

        var mapping = resolveScopeMapping(httpMethod, apiPath);
        if (mapping.isEmpty()) return false;

        var requiredScope = mapping.get().getRequiredScope();
        var tokenScopes = tokenScope.split("\\s+");
        for (var scope : tokenScopes) {
            if (scope.equals(requiredScope)) return true;
        }
        return false;
    }

    private Optional<AuthApiScopeMapping> resolveScopeMapping(String httpMethod, String apiPath) {
        var mapping = apiScopeMappingRepository
                .findByHttpMethodAndApiPatternAndStatus(httpMethod, apiPath, "ACTIVE");
        if (mapping.isPresent()) return mapping;

        var pathParts = apiPath.split("/");
        if (pathParts.length > 2) {
            var wildcardPath = "/" + pathParts[1] + "/**";
            return apiScopeMappingRepository
                    .findByHttpMethodAndApiPatternAndStatus(httpMethod, wildcardPath, "ACTIVE");
        }

        return Optional.empty();
    }
}
