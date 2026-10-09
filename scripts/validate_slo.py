#!/usr/bin/env python3
"""Valida el contrato de slo.yaml antes de que la plataforma lo consuma."""

import sys

import yaml

with open(sys.argv[1] if len(sys.argv) > 1 else "slo.yaml") as f:
    slo = yaml.safe_load(f)

errors = []
for key in ("service", "journey", "owner", "selector", "slis", "policy", "profiles"):
    if key not in slo:
        errors.append(f"falta la clave '{key}'")

for name, sli in slo.get("slis", {}).items():
    objective = sli.get("objective")
    if not isinstance(objective, (int, float)) or not 90 <= objective < 100:
        errors.append(f"slis.{name}.objective debe estar en [90, 100): {objective}")
    if name == "latency" and not isinstance(sli.get("threshold_ms"), int):
        errors.append("slis.latency.threshold_ms debe ser un entero en milisegundos")

for profile in ("prod", "poc"):
    windows = slo.get("profiles", {}).get(profile, {})
    for burn in ("fast_burn", "slow_burn"):
        if not {"long", "short", "threshold"} <= set(windows.get(burn, {})):
            errors.append(f"profiles.{profile}.{burn} requiere long, short y threshold")

if errors:
    print("SLO inválido:\n  - " + "\n  - ".join(errors))
    sys.exit(1)
print(f"SLO válido · {slo['service']} · disponibilidad {slo['slis']['availability']['objective']}% "
      f"· latencia {slo['slis']['latency']['objective']}% < {slo['slis']['latency']['threshold_ms']} ms")
