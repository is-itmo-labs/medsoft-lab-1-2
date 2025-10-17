#!/bin/bash
set -e
mkdir -p certs && cd certs

echo "Генерация CA (самоподписанный корневой сертификат)"
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/C=RU/ST=Moscow/L=Moscow/O=MedSoft/OU=DevCA/CN=MedSoftCA" \
  -out ca.crt

# --- Функция генерации серверного сертификата ---
gen_server_cert() {
  local NAME=$1
  local CN=$2
  echo "Генерация сертификата для ${NAME} (${CN})"
  openssl genrsa -out ${NAME}.key 2048
  openssl req -new -key ${NAME}.key -out ${NAME}.csr \
    -subj "/C=RU/ST=Moscow/L=Moscow/O=MedSoft/OU=${NAME}/CN=${CN}"
  openssl x509 -req -in ${NAME}.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out ${NAME}.crt -days 365 -sha256 \
    -extfile <(printf "subjectAltName=DNS:${CN},IP:127.0.0.1")
  openssl pkcs12 -export -in ${NAME}.crt -inkey ${NAME}.key -out ${NAME}.p12 \
    -name "${NAME}cert" -passout pass:changeit
}

gen_server_cert hospital-chief localhost
gen_server_cert reception localhost
gen_server_cert doctor localhost

echo "Создание truststore.jks с CA"
keytool -importcert -trustcacerts -alias medsoftca -file ca.crt \
  -keystore truststore.jks -storepass changeit -noprompt

echo "Все сертификаты сгенерированы успешно:"
ls -1 *.p12 *.crt truststore.jks
