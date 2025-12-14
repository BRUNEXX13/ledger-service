import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    transfer_only: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 3000,
      stages: [
        { target: 200, duration: '30s' },
        { target: 500, duration: '1m' },
        { target: 1000, duration: '2m' },
        { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = 'http://localhost:8082/api/v1';
const HEADERS = { 'Content-Type': 'application/json' };

// Universo de contas (baseado no V4__insert_heavy_load_data.sql)
const MAX_ACCOUNT_ID = 110002;

export default function () {
  const sender = randomIntBetween(1, MAX_ACCOUNT_ID);
  let receiver = randomIntBetween(1, MAX_ACCOUNT_ID);
  
  while (receiver === sender) {
    receiver = randomIntBetween(1, MAX_ACCOUNT_ID);
  }
  
  const payload = JSON.stringify({
    senderAccountId: sender,
    receiverAccountId: receiver,
    amount: randomIntBetween(1, 50),
    idempotencyKey: uuidv4(),
  });

  const res = http.post(`${BASE_URL}/transfers`, payload, { headers: HEADERS });

  if (res.status !== 202) {
      console.log(`Transfer Failed: Status ${res.status} Body: ${res.body}`);
  }

  check(res, {
    'transfer status is 202': (r) => r.status === 202,
  });
}
