# Security policy

Do not report secrets in a public issue. If a credential appears in git history,
revoke it first, then remove it from every active service and repository copy.

The repository must never contain service-account JSON, private keys, keystores,
or passwords. `google-services.json` is Android client configuration, not an FCM
send credential; its API key should still be restricted to the expected Android
application/signing identities in Google Cloud where applicable.

Production releases must use the same securely archived signing keystore. The
GitHub release workflow reconstructs the keystore only in the ephemeral runner
and does not upload it as an artifact.
