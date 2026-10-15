# OpenShift deployment

Manifests for running NEET Chat on OpenShift (tested on the Developer Sandbox). The image is built in the cluster from this repository's `Dockerfile`, which bundles the `Questions/` PDFs.

| File | Creates |
| --- | --- |
| `imagestreams.yaml` | `openjdk-17-runtime` (base image) and `neet-chat` (build output) |
| `buildconfig.yaml` | Docker build of `main` from GitHub |
| `deployment.yaml` | The app; redeploys when a new image is built |
| `service.yaml` | Port 8080 inside the cluster |
| `route.yaml` | Public HTTPS URL with a 5 minute timeout |
| `secret.yaml.example` | Template for the API key secret (not applied) |

## Deploy

```text
oc create secret generic neet-chat-secrets --from-literal=NEET_LLM_API_KEY=<key>
oc apply -f openshift/
oc get route neet-chat
```

Applying the BuildConfig starts the first build. The pod becomes ready about 40 seconds after it starts, once the papers are indexed.

## Update

- New code: push to `main`, then `oc start-build neet-chat --follow`.
- New API key: `oc set data secret/neet-chat-secrets NEET_LLM_API_KEY=<key>`, then `oc rollout restart deployment/neet-chat`.
- Other LLM providers: set `NEET_LLM_BASE_URL` and `NEET_LLM_MODEL` on the deployment.
