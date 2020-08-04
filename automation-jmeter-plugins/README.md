# automation-jmeter-plugins
This module holds the following Custom JMeter Functions developed by us.

### OAuth2Token - 

- String: Cloud Provider (GCP, AWS, AZURE - only GCP implementation for now)
- Env Var: FIRECLOUD_SERVICE_ACCT_CREDS: Service account key used for SAM User Authorization purpose
- Env Var: SAM_USER: Email account for OAuth2 Authorization

### Dependencies (see build.gradle)

- JMeter core library (ApacheJMeter_core)
- Google OAuth2 library google-auth-library-oauth2-http

### Deployment
Use any CI/CD to deploy the automation-jmeter-plugins-${version}.jar in build/libs to JMeter Home lib/ext 
on any slave containers for any JMeter script to use these custom functions.

### Usage 
#### OAuth2Token - 
Use in any JMeter setUp Thread Group to set a Bearer token property for subsequent test run

- ${__OAuth2Token(GCP, FIRECLOUD_SERVICE_ACCT_CREDS, SAM_USER)
