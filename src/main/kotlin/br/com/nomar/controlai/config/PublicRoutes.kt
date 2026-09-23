package br.com.nomar.controlai.config

// Routes open without authentication (see the permitAll rules in SecurityConfig), so the guards
// that gate authenticated requests skip them
val PUBLIC_ROUTE_PATTERNS = listOf("/auth/**", "/webhooks/**", "/health", "/actuator/health")
