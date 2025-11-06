# Project: CCAPI (Claude Compatible API) Client

## Current Active Instructions

### Goal
Create a Java client for different Claude compatible API provider website.

### Key Requirements
- Use session key authentication via cookies
- Use headers and behavior similar to browser 
- Support all CCAPI compatible endpoints (usage, conversations, chat, attachments)
- Generate POJOs (fluent, lombok) for all API responses
- Save API response samples for reference
- Follow Maven project structure

### Technical Approach
- Use OkHttp3 for HTTP requests
- Use Jackson for JSON processing
- Use Lombok to reduce boilerplate
- Implement proper error handling
- Follow Java 21 standards with modern features
- Use `var` for local variables as per guidelines

### API Endpoints to Implement
1. Usage monitoring: `/api/organizations/{org_id}/usage`
2. Conversations: `/api/organizations/{org_id}/chat_conversations`
3. Chat completion: `/api/organizations/{org_id}/chat_conversations/{conv_id}/completion`
4. File upload: `/api/convert_document`

## Project Requirements

- Maven-based Java project
- Artifact ID: xyz-jphil-ccapis
- Group ID: io.github.xyz-jphil
- Version: 1.0
- Package: xyz.jphil.ccapis

## Pitfalls

- Providing precise http requests as per standard (not well defined, and subject to empirical verification and breakage on undocumented updates in the service)
- Session keys expire and need to be refreshed
- As we might also use this on free plan, stricter rate limiting may apply to API calls
- CORS and security headers must exactly meet (undocumented) server expectations