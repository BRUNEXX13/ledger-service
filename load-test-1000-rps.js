import http from 'k6/http';
import { check, fail } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Configuração do Teste para 1000 RPS constantes
export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      
      // Taxa alvo: 1000 iterações por segundo
      rate: 1000,
      
      // Unidade de tempo da taxa (1s)
      timeUnit: '1s',
      
      // Duração do teste
      duration: '1m',
      
      // Número de VUs a serem pré-alocados antes do teste começar
      preAllocatedVUs: 100,
      
      // Número máximo de VUs permitidos (o k6 escalará até este número se necessário para manter a taxa)
      maxVUs: 2000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // Taxa de erro deve ser menor que 1%
    http_req_duration: ['p(95)<500'], // 95% das requisições devem ser mais rápidas que 500ms
  },
};

const BASE_URL = 'http://localhost:8082/api/v1';
const HEADERS = { 'Content-Type': 'application/json' };
const MAX_USER_ID = 100000;

// Função auxiliar para criar o payload
function createPayload() {
  const senderId = randomIntBetween(1, MAX_USER_ID);
  let receiverId = randomIntBetween(1, MAX_USER_ID);
  
  while (receiverId === senderId) {
    receiverId = randomIntBetween(1, MAX_USER_ID);
  }

  return JSON.stringify({
    senderAccountId: senderId,
    receiverAccountId: receiverId,
    amount: 10.00, // Valor fixo para simplificar
    idempotencyKey: uuidv4(),
  });
}

export function setup() {
  console.log('🚀 Iniciando Setup: Verificando saúde da API...');
  const res = http.post(`${BASE_URL}/transfers`, createPayload(), { headers: HEADERS });
  
  if (res.status !== 202) {
    console.error(`❌ Setup falhou! API retornou status ${res.status}. Abortando teste.`);
    fail('Setup failed - API not healthy');
  }
  console.log('✅ Setup concluído: API está respondendo corretamente (202 Accepted).');
}

export default function () {
  const res = http.post(`${BASE_URL}/transfers`, createPayload(), { headers: HEADERS });

  // Validações
  check(res, {
    'status is 202': (r) => r.status === 202,
  });
}

export function teardown() {
  console.log('🏁 Iniciando Teardown: Verificando saúde da API pós-teste...');
  const res = http.post(`${BASE_URL}/transfers`, createPayload(), { headers: HEADERS });
  
  if (res.status !== 202) {
    console.error(`❌ Teardown falhou! API retornou status ${res.status} após a carga.`);
  } else {
    console.log('✅ Teardown concluído: API sobreviveu e continua respondendo corretamente.');
  }
}
