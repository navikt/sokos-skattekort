#!/bin/bash
export VAULT_ADDR=https://vault.adeo.no

# Extract single values from map[key:value ...]
getFromMap() {
  local map="$1" key="$2"
  # Strip leading 'map[' and trailing ']'
  local body=${map#map[}
  body=${body%]}
  # Turn spaces into newlines, then split on first ':'
  echo "$body" | tr ' ' '\n' | awk -F':' -v k="$key" '$1==k {print substr($0, index($0,":")+1)}'
}

# Ensure user is authenicated, and run login if not.
gcloud auth print-identity-token &> /dev/null
if [ $? -gt 0 ]; then
    gcloud auth login
fi
kubectl config use-context dev-gcp
kubectl config set-context --current --namespace=okonomi

# Get database username and password secret from Vault
[[ "$(vault token lookup -format=json | jq '.data.display_name' -r; exit ${PIPESTATUS[0]})" =~ "nav.no" ]] &>/dev/null || vault login -method=oidc -no-print

# Get AZURE system variables
envValue=$(kubectl exec -it $(kubectl get pods | grep sokos-skattekort | cut -f1 -d' ') -c sokos-skattekort -- env | egrep "^AZURE|^MASKINPORTEN|^MQ_SERVICE"| sort)

POSTGRES_USER_MAP=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-skattekort-user)
POSTGRES_ADMIN_MAP=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-skattekort-admin)

# Set AZURE as local environment variables
rm -f defaults.properties
echo "$envValue" > defaults.properties
echo "AZURE stores as defaults.properties"

echo "POSTGRES_USER_USERNAME=$(getFromMap "$POSTGRES_USER_MAP" username)" >> defaults.properties
echo "POSTGRES_USER_PASSWORD=$(getFromMap "$POSTGRES_USER_MAP" password)" >> defaults.properties

echo "POSTGRES_ADMIN_USERNAME=$(getFromMap "$POSTGRES_ADMIN_MAP" username)" >> defaults.properties
echo "POSTGRES_ADMIN_PASSWORD=$(getFromMap "$POSTGRES_ADMIN_MAP" password)" >> defaults.properties
