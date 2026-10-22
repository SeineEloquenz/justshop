use axum::body::Body;
use axum::extract::{Form, State};
use axum::http::{header, HeaderMap, Request, StatusCode};
use axum::routing::{get, post};
use axum::{Json, Router};
use base64::engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD};
use base64::Engine;
use openidconnect::core::{
    CoreIdToken, CoreIdTokenClaims, CoreJsonWebKeySet, CoreJwsSigningAlgorithm,
    CoreRsaPrivateSigningKey,
};
use openidconnect::url::Url;
use openidconnect::{
    AccessToken, Audience, EmptyAdditionalClaims, EndUserUsername, IssuerUrl, JsonWebKeyId, Nonce,
    PrivateSigningKey, StandardClaims, SubjectIdentifier,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::sync::{Arc, Mutex, RwLock};
use tower::ServiceExt;

use justshop_backend::api::{AppState, Users};
use justshop_backend::app;
use justshop_backend::auth::{OidcAuth, OidcConfig, Sessions};

const CLIENT_ID: &str = "justshop";
const CLIENT_SECRET: &str = "client-secret";
const REDIRECT: &str = "nz.eloque.justshop://oauth";
const VERIFIER: &str = "verifier-0123456789-0123456789-0123456789-0123456789";
const STATE: &str = "state-0123456789abcdef";
const NONCE: &str = "nonce-0123456789abcdef";

struct Grant {
    challenge: String,
    nonce: String,
    redirect_uri: String,
}

#[derive(Clone)]
struct Idp {
    issuer: String,
    key: Arc<CoreRsaPrivateSigningKey>,
    grants: Arc<Mutex<HashMap<String, Grant>>>,
}

#[derive(Deserialize)]
struct TokenForm {
    code: String,
    code_verifier: String,
    redirect_uri: String,
}

fn challenge(verifier: &str) -> String {
    URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()))
}

async fn token(State(idp): State<Idp>, headers: HeaderMap, Form(form): Form<TokenForm>) -> (StatusCode, Json<Value>) {
    let invalid = (StatusCode::BAD_REQUEST, Json(json!({ "error": "invalid_grant" })));
    let expected_auth = format!("Basic {}", STANDARD.encode(format!("{CLIENT_ID}:{CLIENT_SECRET}")));
    if headers.get(header::AUTHORIZATION).and_then(|v| v.to_str().ok()) != Some(&expected_auth) {
        return (StatusCode::UNAUTHORIZED, Json(json!({ "error": "invalid_client" })));
    }
    let Some(grant) = idp.grants.lock().unwrap().remove(&form.code) else {
        return invalid;
    };
    if challenge(&form.code_verifier) != grant.challenge || form.redirect_uri != grant.redirect_uri {
        return invalid;
    }

    let now = chrono::Utc::now();
    let claims = CoreIdTokenClaims::new(
        IssuerUrl::new(idp.issuer.clone()).unwrap(),
        vec![Audience::new(CLIENT_ID.into())],
        now + chrono::Duration::minutes(5),
        now,
        StandardClaims::new(SubjectIdentifier::new("subject-1".into()))
            .set_preferred_username(Some(EndUserUsername::new("alice".into()))),
        EmptyAdditionalClaims {},
    )
    .set_nonce(Some(Nonce::new(grant.nonce)));
    let access_token = AccessToken::new("access-token".into());
    let id_token = CoreIdToken::new(
        claims,
        idp.key.as_ref(),
        CoreJwsSigningAlgorithm::RsaSsaPkcs1V15Sha256,
        Some(&access_token),
        None,
    )
    .unwrap();

    (
        StatusCode::OK,
        Json(json!({
            "access_token": access_token.secret(),
            "token_type": "Bearer",
            "expires_in": 900,
            "id_token": id_token.to_string(),
            })),
    )
}

async fn spawn_idp() -> Idp {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let issuer = format!("http://{}", listener.local_addr().unwrap());
    let key = CoreRsaPrivateSigningKey::from_pem(
        include_str!("fixtures/idp-key.pem"),
        Some(JsonWebKeyId::new("key-1".into())),
    )
    .unwrap();
    let idp = Idp { issuer: issuer.clone(), key: Arc::new(key), grants: Arc::default() };

    let discovery = json!({
        "issuer": issuer,
        "authorization_endpoint": format!("{issuer}/authorize"),
        "token_endpoint": format!("{issuer}/token"),
        "jwks_uri": format!("{issuer}/jwks"),
        "response_types_supported": ["code"],
        "subject_types_supported": ["public"],
        "id_token_signing_alg_values_supported": ["RS256"],
    });
    let jwks = serde_json::to_value(CoreJsonWebKeySet::new(vec![idp.key.as_verification_key()])).unwrap();

    let router = Router::new()
        .route("/.well-known/openid-configuration", get(move || async move { Json(discovery) }))
        .route("/jwks", get(move || async move { Json(jwks) }))
        .route("/token", post(token))
        .with_state(idp.clone());
    tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
    idp
}

fn oidc_state(idp: &Idp) -> AppState {
    let config = OidcConfig {
        issuer: idp.issuer.clone(),
        client_id: CLIENT_ID.into(),
        client_secret: CLIENT_SECRET.into(),
        scopes: vec!["openid".into(), "profile".into()],
        session_idle_timeout: 3600,
    };
    let mut lists = HashMap::new();
    lists.insert("junkyard".to_string(), HashMap::new());
    AppState {
        shopping_list: Arc::new(RwLock::new(lists)),
        users: Users::default(),
        list_changed: Arc::default(),
        oidc: Some(Arc::new(OidcAuth::new(config, Sessions::default()).unwrap())),
    }
}

async fn send(state: &AppState, method: &str, uri: &str, token: Option<&str>, body: Option<Value>) -> (StatusCode, Value) {
    let mut request = Request::builder().method(method).uri(uri);
    if let Some(token) = token {
        request = request.header(header::AUTHORIZATION, format!("Bearer {token}"));
    }
    let body = match body {
        Some(body) => {
            request = request.header(header::CONTENT_TYPE, "application/json");
            Body::from(body.to_string())
        }
        None => Body::empty(),
    };
    let response = app(state.clone()).oneshot(request.body(body).unwrap()).await.unwrap();
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX).await.unwrap();
    (status, serde_json::from_slice(&bytes).unwrap_or(Value::Null))
}

/// Runs `start` and plays the provider's authorization step, returning the issued code.
async fn authorize(state: &AppState, idp: &Idp) -> String {
    let (status, body) = send(state, "POST", "/v2/auth/oidc/start", None, Some(json!({
        "redirect_uri": REDIRECT,
        "state": STATE,
        "nonce": NONCE,
        "code_challenge": challenge(VERIFIER),
    })))
    .await;
    assert_eq!(status, StatusCode::OK);

    let url = Url::parse(body["url"].as_str().unwrap()).unwrap();
    assert_eq!(url.as_str().split('?').next().unwrap(), format!("{}/authorize", idp.issuer));
    let params: HashMap<_, _> = url.query_pairs().into_owned().collect();
    assert_eq!(params["response_type"], "code");
    assert_eq!(params["client_id"], CLIENT_ID);
    assert_eq!(params["redirect_uri"], REDIRECT);
    assert_eq!(params["state"], STATE);
    assert_eq!(params["nonce"], NONCE);
    assert_eq!(params["code_challenge"], challenge(VERIFIER));
    assert_eq!(params["code_challenge_method"], "S256");
    assert!(params["scope"].split(' ').any(|s| s == "openid"));

    let code = "authorization-code".to_string();
    idp.grants.lock().unwrap().insert(code.clone(), Grant {
        challenge: params["code_challenge"].clone(),
        nonce: params["nonce"].clone(),
        redirect_uri: params["redirect_uri"].clone(),
    });
    code
}

fn finish_body(code: &str, verifier: &str, nonce: &str) -> Value {
    json!({ "redirect_uri": REDIRECT, "code": code, "code_verifier": verifier, "nonce": nonce })
}

#[tokio::test]
async fn login_issues_a_session_that_authorizes_api_calls() {
    let idp = spawn_idp().await;
    let state = oidc_state(&idp);
    let code = authorize(&state, &idp).await;

    let (status, body) = send(&state, "POST", "/v2/auth/oidc/finish", None, Some(finish_body(&code, VERIFIER, NONCE))).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["user"], "alice");
    let token = body["token"].as_str().unwrap().to_owned();

    let uri = "/v2/delete-checked?list_name=junkyard";
    assert_eq!(send(&state, "DELETE", uri, None, None).await.0, StatusCode::UNAUTHORIZED);
    assert_eq!(send(&state, "DELETE", uri, Some("forged"), None).await.0, StatusCode::UNAUTHORIZED);
    assert_eq!(send(&state, "DELETE", uri, Some(&token), None).await.0, StatusCode::OK);

    assert_eq!(send(&state, "POST", "/v2/auth/logout", Some(&token), None).await.0, StatusCode::NO_CONTENT);
    assert_eq!(send(&state, "DELETE", uri, Some(&token), None).await.0, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn finish_rejects_wrong_code_verifier() {
    let idp = spawn_idp().await;
    let state = oidc_state(&idp);
    let code = authorize(&state, &idp).await;

    let wrong = "wrong-verifier-0123456789-0123456789-0123456789-0123";
    let (status, _) = send(&state, "POST", "/v2/auth/oidc/finish", None, Some(finish_body(&code, wrong, NONCE))).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn finish_rejects_wrong_nonce() {
    let idp = spawn_idp().await;
    let state = oidc_state(&idp);
    let code = authorize(&state, &idp).await;

    let (status, _) = send(&state, "POST", "/v2/auth/oidc/finish", None, Some(finish_body(&code, VERIFIER, "other-nonce-0123456789"))).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn start_rejects_foreign_redirect_uri() {
    let idp = spawn_idp().await;
    let state = oidc_state(&idp);

    let (status, _) = send(&state, "POST", "/v2/auth/oidc/start", None, Some(json!({
        "redirect_uri": "https://evil.example/callback",
        "state": STATE,
        "nonce": NONCE,
        "code_challenge": challenge(VERIFIER),
    })))
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
}

#[tokio::test]
async fn auth_config_reports_mode() {
    let idp = spawn_idp().await;
    let (_, body) = send(&oidc_state(&idp), "GET", "/v2/auth-config", None, None).await;
    assert_eq!(body["mode"], "oidc");

    let plain = AppState { shopping_list: Arc::default(), users: Users::default(), list_changed: Arc::default(), oidc: None };
    let (_, body) = send(&plain, "GET", "/v2/auth-config", None, None).await;
    assert_eq!(body["mode"], "none");
    assert_eq!(send(&plain, "POST", "/v2/auth/oidc/start", None, Some(json!({
        "redirect_uri": REDIRECT, "state": STATE, "nonce": NONCE, "code_challenge": challenge(VERIFIER),
    }))).await.0, StatusCode::NOT_FOUND);
}
