import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    transfer_stress_test: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 1000, 
      maxVUs: 3000, 
      stages: [
        { target: 1000, duration: '1m' },  // Aquece para 1k TPS
        { target: 2000, duration: '1m' },  // Sobe para 2k TPS
        { target: 3000, duration: '3m' },  // Mantém 3k TPS por 3 minutos
        { target: 0, duration: '1m' },     // Resfria
      ],
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
