#!/usr/bin/env bash
set -e

if [[ "$RUNNER_DEBUG" == "1" ]]; then
  set -x
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source ${SCRIPT_DIR}/aurora_common.sh

AURORA_ARN=$(aws rds describe-db-clusters \
    --db-cluster-identifier "${AURORA_CLUSTER}" \
    --region "${AURORA_REGION}" \
    --query "DBClusters[0].DBClusterArn" \
    --output text
)
if [ -z "${AURORA_ARN}" ]; then
  echo "Aurora instance '${AURORA_ARN}' not found in the '${AWS_REGION}' region"
  exit 1
fi

SECRET_ARN=$(aws secretsmanager list-secrets \
    --filters "Key=\"name\",Values=\"${AURORA_CREDENTIAL_SECRET}\"" \
    --region "${AURORA_REGION}" \
    --query "SecretList[0].ARN" \
    --output text
)

if [ -z "${SECRET_ARN}" ]; then
  echo "'${AURORA_CREDENTIAL_SECRET}' not found. Creating it..."

  aws secretsmanager create-secret \
      --name "${AURORA_CREDENTIAL_SECRET}" \
      --description "Credentials for Aurora Cluster ${AURORA_CLUSTER}" \
      --region "${AURORA_REGION}" \
      --secret-string "{\"username\":\"${AURORA_USERNAME}\",\"password\":\"${AURORA_PASSWORD}\""
fi

aws rds enable-http-endpoint \
    --resource-arn "${AURORA_ARN}" \
    --region "${AURORA_REGION}"

aws rds-data execute-statement \
    --resource-arn "${AURORA_ARN}" \
    --secret-arn "${SECRET_ARN}" \
    --database "${AURORA_DATABASE}" \
    --sql "${1}"
