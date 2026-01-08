import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    stress_test_10k: {
      executor: 'constant-arrival-rate',
      rate: 10000, // Alvo: 10.000 requisições por segundo
      timeUnit: '1s',
      duration: '5m', // Teste de imersão (Soak Test)

      // Pré-alocação agressiva para suportar o arranque de 10k RPS
      preAllocatedVUs: 5000,
      maxVUs: 30000, // Margem de segurança alta para picos de latência
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // Erros abaixo de 1%
    http_req_duration: ['p(95)<1000'], // Tolerância de 1s
  },
};

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