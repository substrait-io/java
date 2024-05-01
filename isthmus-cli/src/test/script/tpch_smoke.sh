#!/bin/bash

set -eu -o pipefail

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
cd "${parent_path}"
CMD=../../../build/graal/isthmus

TPCH="../../../../isthmus/src/test/resources/tpch"

DDL=$(cat ${TPCH}/schema.sql)
QUERY_FOLDER="${TPCH}/queries"

##for QUERYNUM in {1..22}; do
# TODO: failed query: 8 12 15. 15 failed due to comments
QUERY_TO_RUN=(1 2 3 4 5 6 7 9 10 11 13 14 16 17 18 19 20 21 22)
for QUERY_NUM in "${QUERY_TO_RUN[@]}"; do
     if [ "${QUERY_NUM}" -lt 10 ]; then
       QUERY=$(cat "${QUERY_FOLDER}/0${QUERY_NUM}.sql")
     else
       QUERY=$(cat "${QUERY_FOLDER}/${QUERY_NUM}.sql")
    fi

    echo "Processing tpc-h query ${QUERY_NUM}"
    echo "${QUERY}"
    $CMD "${QUERY}" --create "${DDL}"
done
