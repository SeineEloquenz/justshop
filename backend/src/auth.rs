use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use axum::extract::{Request, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use axum::Json;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use openidconnect::core::{
    CoreAuthenticationFlow, CoreClient, CoreIdToken, CoreIdTokenClaims, CoreProviderMetadata,
};
use openidconnect::{
    reqwest, AccessToken, AccessTokenHash, AuthorizationCode, ClaimsVerificationError, ClientId,
    ClientSecret, CsrfToken, EndpointMaybeSet, EndpointNotSet, EndpointSet, IssuerUrl, Nonce,
    OAuth2TokenResponse, PkceCodeVerifier, RedirectUrl, RequestTokenError, Scope, TokenResponse,
};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use tokio::sync::Notify;
use tracing::{error, info, warn};

use crate::api::AppState;

/// Redirect URIs of the release and debug builds of the Android app.
pub const APP_REDIRECT_URIS: [&str; 2] = [
    "nz.eloque.justshop://oauth",
    "nz.eloque.justshop.dev://oauth",
];

type OidcClient = CoreClient<
    EndpointSet,
    EndpointNotSet,
    EndpointNotSet,
    EndpointNotSet,
    EndpointMaybeSet,
    EndpointMaybeSet,
>;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Session {
    pub user: String,
    pub subject: String,
    pub created_at: u64,
    pub last_used_at: u64,
}

/// Login sessions keyed by the SHA-256 of their bearer token.
#[derive(Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct Sessions(HashMap<String, Session>);

impl Sessions {
    pub fn create(&mut self, user: String, subject: String, now: u64) -> String {
        let token = CsrfToken::new_random_len(32).into_secret();
        let session = Session { user, subject, created_at: now, last_used_at: now };
        self.0.insert(hash_token(&token), session);
        token
    }

    pub fn authenticate(&mut self, token: &str, now: u64, idle_timeout: u64) -> Option<Session> {
        let key = hash_token(token);
        let session = self.0.get_mut(&key)?;
        if now.saturating_sub(session.last_used_at) > idle_timeout {
            self.0.remove(&key);
            return None;
        }
        session.last_used_at = now;
        Some(session.clone())
    }

    pub fn revoke(&mut self, token: &str) -> bool {
        self.0.remove(&hash_token(token)).is_some()
    }

    pub fn prune(&mut self, now: u64, idle_timeout: u64) {
        self.0.retain(|_, s| now.saturating_sub(s.last_used_at) <= idle_timeout);
    }

    pub fn len(&self) -> usize {
        self.0.len()
    }

    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

fn hash_token(token: &str) -> String {
    URL_SAFE_NO_PAD.encode(Sha256::digest(token.as_bytes()))
}

pub fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs()
}

pub struct OidcConfig {
    pub issuer: String,
    pub client_id: String,
    pub client_secret: String,
    pub scopes: Vec<String>,
    /// Seconds a session may go unused before it expires.
    pub session_idle_timeout: u64,
}

pub struct OidcAuth {
    issuer: IssuerUrl,
    client_id: ClientId,
    client_secret: ClientSecret,
    scopes: Vec<Scope>,
    pub session_idle_timeout: u64,
    http: reqwest::Client,
    metadata: tokio::sync::RwLock<Option<CoreProviderMetadata>>,
    pub sessions: Arc<RwLock<Sessions>>,
    pub sessions_changed: Notify,
}

impl OidcAuth {
    pub fn new(config: OidcConfig, sessions: Sessions) -> Result<Self, String> {
        let issuer = IssuerUrl::new(config.issuer).map_err(|e| format!("Invalid issuer URL: {e}"))?;
        let http = reqwest::Client::builder()
            // Following redirects would expose the token exchange to SSRF.
            .redirect(reqwest::redirect::Policy::none())
            .timeout(Duration::from_secs(15))
            .build()
            .map_err(|e| format!("Failed to build HTTP client: {e}"))?;

        Ok(Self {
            issuer,
            client_id: ClientId::new(config.client_id),
            client_secret: ClientSecret::new(config.client_secret),
            scopes: config.scopes.into_iter().map(Scope::new).collect(),
            session_idle_timeout: config.session_idle_timeout,
            http,
            metadata: tokio::sync::RwLock::new(None),
            sessions: Arc::new(RwLock::new(sessions)),
            sessions_changed: Notify::new(),
        })
    }

    pub async fn metadata(&self, refresh: bool) -> Result<CoreProviderMetadata, AuthError> {
        if !refresh {
            if let Some(metadata) = self.metadata.read().await.as_ref() {
                return Ok(metadata.clone());
            }
        }
        let metadata = CoreProviderMetadata::discover_async(self.issuer.clone(), &self.http)
            .await
            .map_err(|e| {
                error!("OIDC discovery for {} failed: {e}", self.issuer.as_str());
                AuthError::Upstream
            })?;
        *self.metadata.write().await = Some(metadata.clone());
        Ok(metadata)
    }

    fn client(&self, metadata: CoreProviderMetadata, redirect: RedirectUrl) -> OidcClient {
        CoreClient::from_provider_metadata(
            metadata,
            self.client_id.clone(),
            Some(self.client_secret.clone()),
        )
        .set_redirect_uri(redirect)
    }
}

#[derive(Debug)]
pub enum AuthError {
    BadRequest(&'static str),
    NotFound,
    Unauthorized,
    Upstream,
}

impl IntoResponse for AuthError {
    fn into_response(self) -> Response {
        match self {
            AuthError::BadRequest(reason) => (StatusCode::BAD_REQUEST, reason).into_response(),
            AuthError::NotFound => StatusCode::NOT_FOUND.into_response(),
            AuthError::Unauthorized => {
                (StatusCode::UNAUTHORIZED, [(header::WWW_AUTHENTICATE, "Bearer")]).into_response()
            }
            AuthError::Upstream => StatusCode::BAD_GATEWAY.into_response(),
        }
    }
}

fn app_redirect(uri: &str) -> Result<RedirectUrl, AuthError> {
    if !APP_REDIRECT_URIS.contains(&uri) {
        return Err(AuthError::BadRequest("unknown redirect_uri"));
    }
    RedirectUrl::new(uri.to_owned()).map_err(|_| AuthError::BadRequest("invalid redirect_uri"))
}

fn is_valid_random_param(value: &str) -> bool {
    (16..=256).contains(&value.len())
        && value.bytes().all(|b| b.is_ascii_alphanumeric() || b"-._~".contains(&b))
}

fn is_s256_challenge(value: &str) -> bool {
    value.len() == 43 && URL_SAFE_NO_PAD.decode(value).is_ok()
}

fn bearer_token(headers: &HeaderMap) -> Option<&str> {
    headers
        .get(header::AUTHORIZATION)?
        .to_str()
        .ok()?
        .strip_prefix("Bearer ")
}

pub async fn auth_config(State(state): State<AppState>) -> Json<serde_json::Value> {
    let mode = if state.oidc.is_some() { "oidc" } else { "none" };
    Json(json!({ "mode": mode }))
}

#[derive(Deserialize)]
pub struct StartRequest {
    pub redirect_uri: String,
    pub state: String,
    pub nonce: String,
    pub code_challenge: String,
}

#[derive(Serialize)]
pub struct StartResponse {
    pub url: String,
}

pub async fn oidc_start(
    State(state): State<AppState>,
    Json(req): Json<StartRequest>,
) -> Result<Json<StartResponse>, AuthError> {
    let oidc = state.oidc.as_deref().ok_or(AuthError::NotFound)?;
    let redirect = app_redirect(&req.redirect_uri)?;
    if !is_valid_random_param(&req.state) || !is_valid_random_param(&req.nonce) {
        return Err(AuthError::BadRequest("invalid state or nonce"));
    }
    if !is_s256_challenge(&req.code_challenge) {
        return Err(AuthError::BadRequest("invalid code_challenge"));
    }

    let client = oidc.client(oidc.metadata(false).await?, redirect);
    let StartRequest { state: csrf, nonce, code_challenge, .. } = req;
    let (url, _, _) = client
        .authorize_url(
            CoreAuthenticationFlow::AuthorizationCode,
            move || CsrfToken::new(csrf),
            move || Nonce::new(nonce),
        )
        .add_scopes(oidc.scopes.iter().cloned())
        .add_extra_param("code_challenge", code_challenge)
        .add_extra_param("code_challenge_method", "S256")
        .url();

    Ok(Json(StartResponse { url: url.to_string() }))
}

#[derive(Deserialize)]
pub struct FinishRequest {
    pub redirect_uri: String,
    pub code: String,
    pub code_verifier: String,
    pub nonce: String,
}

#[derive(Serialize)]
pub struct FinishResponse {
    pub token: String,
    pub user: String,
}

fn verify_id_token(
    client: &OidcClient,
    id_token: &CoreIdToken,
    access_token: &AccessToken,
    nonce: &Nonce,
) -> Result<CoreIdTokenClaims, ClaimsVerificationError> {
    let verifier = client.id_token_verifier();
    let claims = id_token.claims(&verifier, nonce)?;

    if let Some(expected) = claims.access_token_hash() {
        let alg = id_token.signing_alg().map_err(ClaimsVerificationError::SignatureVerification)?;
        let key = id_token
            .signing_key(&verifier)
            .map_err(ClaimsVerificationError::SignatureVerification)?;
        let actual = AccessTokenHash::from_token(access_token, alg, key)
            .map_err(|e| ClaimsVerificationError::Other(e.to_string()))?;
        if actual != *expected {
            return Err(ClaimsVerificationError::Other("access token hash mismatch".into()));
        }
    }
    Ok(claims.clone())
}

pub async fn oidc_finish(
    State(state): State<AppState>,
    Json(req): Json<FinishRequest>,
) -> Result<Json<FinishResponse>, AuthError> {
    let oidc = state.oidc.as_deref().ok_or(AuthError::NotFound)?;
    let redirect = app_redirect(&req.redirect_uri)?;
    if !is_valid_random_param(&req.nonce) {
        return Err(AuthError::BadRequest("invalid nonce"));
    }

    let client = oidc.client(oidc.metadata(false).await?, redirect.clone());
    let response = client
        .exchange_code(AuthorizationCode::new(req.code))
        .map_err(|e| {
            error!("OIDC provider has no token endpoint: {e}");
            AuthError::Upstream
        })?
        .set_pkce_verifier(PkceCodeVerifier::new(req.code_verifier))
        .request_async(&oidc.http)
        .await
        .map_err(|e| match e {
            RequestTokenError::ServerResponse(e) => {
                warn!("Token exchange rejected: {e}");
                AuthError::Unauthorized
            }
            e => {
                error!("Token exchange failed: {e}");
                AuthError::Upstream
            }
        })?;

    let id_token = response.id_token().ok_or_else(|| {
        warn!("Token response contains no ID token");
        AuthError::Unauthorized
    })?;
    let nonce = Nonce::new(req.nonce);

    let claims = match verify_id_token(&client, id_token, response.access_token(), &nonce) {
        Err(ClaimsVerificationError::SignatureVerification(_)) => {
            info!("ID token signature did not verify, refreshing provider keys");
            let client = oidc.client(oidc.metadata(true).await?, redirect);
            verify_id_token(&client, id_token, response.access_token(), &nonce)
        }
        result => result,
    }
    .map_err(|e| {
        warn!("ID token rejected: {e}");
        AuthError::Unauthorized
    })?;

    let subject = claims.subject().as_str().to_owned();
    let user = claims
        .preferred_username()
        .map(|u| u.as_str().to_owned())
        .unwrap_or_else(|| subject.clone());

    let token = oidc.sessions.write().unwrap().create(user.clone(), subject, now());
    oidc.sessions_changed.notify_one();
    info!("Created session for {user}");

    Ok(Json(FinishResponse { token, user }))
}

pub async fn logout(State(state): State<AppState>, headers: HeaderMap) -> StatusCode {
    let (Some(oidc), Some(token)) = (state.oidc.as_deref(), bearer_token(&headers)) else {
        return StatusCode::NOT_FOUND;
    };
    if oidc.sessions.write().unwrap().revoke(token) {
        oidc.sessions_changed.notify_one();
    }
    StatusCode::NO_CONTENT
}

pub async fn require_session(State(state): State<AppState>, mut req: Request, next: Next) -> Response {
    let Some(oidc) = state.oidc.as_deref() else {
        return next.run(req).await;
    };
    let session = bearer_token(req.headers()).and_then(|token| {
        oidc.sessions
            .write()
            .unwrap()
            .authenticate(token, now(), oidc.session_idle_timeout)
    });
    match session {
        Some(session) => {
            req.extensions_mut().insert(session);
            next.run(req).await
        }
        None => AuthError::Unauthorized.into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const DAY: u64 = 86400;

    #[test]
    fn session_is_valid_until_idle_timeout() {
        let mut sessions = Sessions::default();
        let token = sessions.create("alice".into(), "sub".into(), 0);

        assert!(sessions.authenticate(&token, 10 * DAY, 30 * DAY).is_some());
        assert!(sessions.authenticate(&token, 40 * DAY, 30 * DAY).is_some());
        assert!(sessions.authenticate(&token, 71 * DAY, 30 * DAY).is_none());
        assert!(sessions.is_empty());
    }

    #[test]
    fn unknown_and_revoked_tokens_are_rejected() {
        let mut sessions = Sessions::default();
        let token = sessions.create("alice".into(), "sub".into(), 0);

        assert!(sessions.authenticate("nope", 0, DAY).is_none());
        assert!(sessions.revoke(&token));
        assert!(sessions.authenticate(&token, 0, DAY).is_none());
    }

    #[test]
    fn tokens_are_not_stored_in_plain_text() {
        let mut sessions = Sessions::default();
        let token = sessions.create("alice".into(), "sub".into(), 0);

        let stored = serde_json::to_string(&sessions).unwrap();
        assert!(!stored.contains(&token));
    }

    #[test]
    fn prune_drops_idle_sessions() {
        let mut sessions = Sessions::default();
        sessions.create("alice".into(), "a".into(), 0);
        sessions.create("bob".into(), "b".into(), 20 * DAY);

        sessions.prune(35 * DAY, 30 * DAY);
        assert_eq!(sessions.len(), 1);
    }

    #[test]
    fn only_app_redirects_are_accepted() {
        assert!(app_redirect("nz.eloque.justshop://oauth").is_ok());
        assert!(app_redirect("https://evil.example/cb").is_err());
    }
}
