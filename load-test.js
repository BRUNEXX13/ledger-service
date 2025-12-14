import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    transfer_stress_test: {
      executor: 'ramping-arrival-rate',
      startRate: 100, // Começa mais agressivo
      timeUnit: '1s',
      preAllocatedVUs: 500, // Mais VUs pré-alocados
      maxVUs: 5000, // Permite até 5000 VUs para tentar atingir a meta
      stages: [
        { target: 1000, duration: '1m' },  // Aquece para 1k TPS
        { target: 5000, duration: '2m' },  // Sobe para 5k TPS
        { target: 10000, duration: '2m' }, // Tenta atingir 10k TPS (Carga Extrema)
        { target: 10000, duration: '2m' }, // Mantém 10k TPS
        { target: 0, duration: '1m' },     // Resfria
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // Erros abaixo de 1%
    http_req_duration: ['p(95)<1000'], // 95% das requisições em menos de 1s
  },
};

// Pega a URL da variável de ambiente ou usa localhost como padrão
const BASE_URL = __ENV.API_URL || 'http://localhost:8082/api/v1';
const HEADERS = { 'Content-Type': 'application/json' };

const MAX_ACCOUNT_ID = 100000; 

export default function () {
  const sender = randomIntBetween(1, MAX_ACCOUNT_ID);
  let receiver = randomIntBetween(1, MAX_ACCOUNT_ID);
  
  while (receiver === sender) {
    receiver = randomIntBetween(1, MAX_ACCOUNT_ID);
  }
  
  const payload = JSON.stringify({
    senderAccountId: sender,
    receiverAccountId: receiver,
    amount: randomIntBetween(1, 100),
    idempotencyKey: uuidv4(),
  });

  const res = http.post(`${BASE_URL}/transfers`, payload, { headers: HEADERS });

  check(res, {
    'status is 202': (r) => r.status === 202,
  });
}
