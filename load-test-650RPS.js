import http from 'k6/http';
import { check, fail } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    transfer_stress_test: {
      executor: 'constant-arrival-rate',
      rate: 6500, // 🎯 AJUSTADO PARA 6.500 RPS (Meta Segura Local)
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 1000,
      maxVUs: 7000, // Reduzido levemente para economizar recursos da máquina de teste
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // Erros abaixo de 1%
    http_req_duration: ['p(95)<500'], // Meta de 500ms
  },
};

// Pega a URL da variável de ambiente ou usa localhost como padrão
const BASE_URL = __ENV.API_URL || 'http://localhost:8082/api/v1';
const HEADERS = { 'Content-Type': 'application/json' };

const MAX_ACCOUNT_ID = 100000; 

// Função auxiliar para criar o payload
function createPayload() {
  const sender = randomIntBetween(1, MAX_ACCOUNT_ID);
  let receiver = randomIntBetween(1, MAX_ACCOUNT_ID);
  
  while (receiver === sender) {
    receiver = randomIntBetween(1, MAX_ACCOUNT_ID);
  }
  
  return JSON.stringify({
    senderAccountId: sender,
    receiverAccountId: receiver,
    amount: randomIntBetween(1, 100),
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
