#!/bin/bash
# 写 app/google-services.json：有 GOOGLE_SERVICES_JSON 就用它，没有就写一个占位的，只为能编译（同步不可用）
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
  printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json
  exit 0
fi
[ -f app/google-services.json ] && exit 0
cat > app/google-services.json <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "carnet-local-placeholder",
    "storage_bucket": "carnet-local-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": { "package_name": "com.personal.carnet" }
      },
      "oauth_client": [],
      "api_key": [{ "current_key": "placeholder" }],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
JSON
