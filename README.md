# bancoplus-payments-qr

**Owner:** tribu-pagos · **Servicio:** `payments-qr` (journey pago-qr) · **Plataforma:** Amazon EKS (Graviton)

Servicio de pagos QR en Spring Boot **sin dependencias de OpenTelemetry**. La telemetría la provee la
plataforma: el OpenTelemetry Operator inyecta el agente Java al crear el pod (bancoplus-platform-gitops,
`observability/instrumentation-rules`).

## Responsabilidades del equipo

| Archivo | Contrato |
|---|---|
| `src/` | Respuestas con `X-Business-Operation`, `X-Business-Outcome`, `X-Business-Reason` |
| `slo.yaml` | Objetivos de disponibilidad y latencia del servicio (you build it, you run it) |
| `Dockerfile` | Imagen arm64, usuario no root (uid 10001), runtime Alpine |

## Contrato de negocio

| Escenario | HTTP | `X-Business-Outcome` | `X-Business-Reason` |
|---|---|---|---|
| Aprobado | 201 | `approved` | `OK` |
| Rechazo por riesgo | 500 | `declined` | `RIESGO_ALTO` |
| Fondos insuficientes | 200 | `declined` | `FONDOS_INSUFICIENTES` |
| Falla de sistema | 200 | `failed` | `ERROR_SISTEMA` |

El rechazo como 500 y la falla como 200 son anti-patrones heredados que la plataforma corrige en el
Gateway sin cambiar el código.

## Pipeline (`.github/workflows/ci.yaml`)

```
PR / push ─► Contrato SLO ─► Build arm64 ─► Trivy (HIGH/CRITICAL bloqueante)
                                                  └─► (solo main) OIDC ─► ECR :<commit-sha>
```

| Control | Implementación |
|---|---|
| Sin credenciales estáticas | `aws-actions/configure-aws-credentials` asume el rol vía OIDC (`id-token: write`) |
| Origen autorizado | El rol solo acepta `repo:anfedimo/bancoplus-payments-qr:ref:refs/heads/main` |
| Trazabilidad | Tag inmutable = commit SHA (ECR con `IMMUTABLE`) |
| Seguridad de la imagen | Trivy bloquea vulnerabilidades HIGH/CRITICAL con parche disponible |

Configuración del repositorio (Settings → Secrets and variables → Actions → Variables):

| Variable | Valor |
|---|---|
| `AWS_ROLE_ARN` | output `payments_ci_role_arn` de `stacks/aws-eks-ephemeral/ci-identity` (bancoplus-reliability-platform-iac) |

## Desarrollo local

```bash
docker build --platform linux/arm64 -t payments-qr:dev .                 # Construye imagen arm64 local
docker run -p 8080:8080 -e FRAUD_API_URL=http://localhost:8080 payments-qr:dev   # Ejecuta servicio en puerto 8080
python3 scripts/validate_slo.py slo.yaml                                  # Valida contrato del SLO
```
