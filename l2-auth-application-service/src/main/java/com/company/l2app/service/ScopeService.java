package com.company.l2app.service;

import com.company.l2app.repository.AuthClientScopeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScopeService {

    private final AuthClientScopeRepository clientScopeRepository;

    public ScopeService(AuthClientScopeRepository clientScopeRepository) {
        this.clientScopeRepository = clientScopeRepository;
    }

    public String getClientScopeString(String clientId) {
        var scopes = clientScopeRepository.findByClientId(clientId);
        return scopes.stream()
                .map(s -> s.getScopeName())
                .collect(Collectors.joining(" "));
    }
}
