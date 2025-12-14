import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    // Cenário de Carga Variável (Ramping)
    mixed_traffic: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 2000, // Aumentado para suportar latência maior se houver
      stages: [
        { target: 200, duration: '30s' },  // Aquecimento
        { target: 500, duration: '1m' },   // Carga média
        { target: 1000, duration: '2m' },  // Carga alvo (1000 TPS)
        { target: 0, duration: '30s' },    // Resfriamento
      ],
    },
  },
  thresholds: {
    // Taxa de erro global deve ser menor que 1%
    http_req_failed: ['rate<0.01'], 
    // 95% das requisições devem ser atendidas em menos de 500ms
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = 'http://localhost:8082/api/v1';
const HEADERS = { 'Content-Type': 'application/json' };

// IDs de contas que sabemos que existem (do V1__insert_initial_data.sql)
// Para um teste real de 1000 TPS, idealmente teríamos milhares de contas.
// Com apenas 2 contas, o Lock de Banco será o gargalo absoluto em escritas.
const ACCOUNTS = [1, 2]; 

export default function () {
  // Distribuição de Carga:
  // 20% Transferências (Escrita - Pesado)
  // 40% Consulta de Conta (Leitura - Cacheável)
  // 40% Consulta de Transação (Leitura - Cacheável)
  
  const rand = Math.random();

  if (rand < 0.2) {
    makeTransfer();
  } else if (rand < 0.6) {
    getAccount();
  } else {
    getTransaction();
  }
}

function makeTransfer() {
  const sender = randomIntBetween(1, 2);
  const receiver = sender === 1 ? 2 : 1;
  
  const payload = JSON.stringify({
    senderAccountId: sender,
    receiverAccountId: receiver,
    amount: randomIntBetween(1, 50), // Valores baixos para não zerar o saldo rápido
    idempotencyKey: uuidv4(),
  });

  const res = http.post(`${BASE_URL}/transfers`, payload, { headers: HEADERS });

  check(res, {
    'transfer status is 202': (r) => r.status === 202,
  });
}

function getAccount() {
  const accountId = randomIntBetween(1, 2);
  const res = http.get(`${BASE_URL}/accounts/${accountId}`, { headers: HEADERS });
  
  check(res, {
    'get account status is 200': (r) => r.status === 200,
  });
}

function getTransaction() {
  // Tenta buscar uma transação aleatória (pode dar 404, o que é ok para teste de carga)
  // Em um cenário real, usaríamos IDs coletados de transferências anteriores
  const txId = randomIntBetween(1, 100); 
  const res = http.get(`${BASE_URL}/transactions/${txId}`, { headers: HEADERS });
  
  // Aceitamos 200 (achou) ou 404 (não achou), mas não 500
  check(res, {
    'get tx status is 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
}
