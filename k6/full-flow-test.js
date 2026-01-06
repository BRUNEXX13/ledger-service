import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  scenarios: {
    full_lifecycle_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 }, // Aquecimento (Ramp-up)
        { duration: '2m', target: 200 }, // Aumentar para 200 VUs simultâneos
        { duration: '30s', target: 0 },  // Resfriamento (Ramp-down)
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // Erros abaixo de 1%
    http_req_duration: ['p(95)<1000'], // 95% das requisições abaixo de 1s (fluxo completo é mais lento)
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8082/api/v1';
const HEADERS = { 'Content-Type': 'application/json' };

// Função auxiliar para gerar CPF/CNPJ fictício (apenas números para evitar colisão de unique constraint)
function generateDocument() {
  const n = Math.floor(Math.random() * 100000000000);
  return n.toString().padStart(11, '0');
}

export default function () {
  const uniqueId = uuidv4().substring(0, 8);

  // --- 1. Criação do Usuário Remetente (Sender) ---
  // Assume-se que a criação já define um saldo inicial ou cria a conta
  const senderPayload = JSON.stringify({
    name: `Sender ${uniqueId}`,
    document: generateDocument(),
    email: `sender.${uniqueId}@test.com`,
    password: 'password123',
    balance: 1000.00, // Saldo suficiente para transferir
    userType: 'COMMON'
  });

  const createSenderRes = http.post(`${BASE_URL}/users`, senderPayload, { headers: HEADERS });
  
  const senderCreated = check(createSenderRes, {
    'Sender created (201/200)': (r) => r.status === 201 || r.status === 200,
  });

  if (!senderCreated) {
    console.log(`Falha ao criar sender: ${createSenderRes.status} - ${createSenderRes.body}`);
    return; // Interrompe iteração se falhar
  }

  const senderId = createSenderRes.json('id');

  // --- 2. Criação do Usuário Destinatário (Receiver) ---
  const receiverPayload = JSON.stringify({
    name: `Receiver ${uniqueId}`,
    document: generateDocument(),
    email: `receiver.${uniqueId}@test.com`,
    password: 'password123',
    balance: 0.00,
    userType: 'COMMON'
  });

  const createReceiverRes = http.post(`${BASE_URL}/users`, receiverPayload, { headers: HEADERS });
  
  const receiverCreated = check(createReceiverRes, {
    'Receiver created (201/200)': (r) => r.status === 201 || r.status === 200,
  });

  if (!receiverCreated) {
     return;
  }

  const receiverId = createReceiverRes.json('id');

  // --- 3. Listagem/Consulta (Verifica persistência) ---
  const getSenderRes = http.get(`${BASE_URL}/users/${senderId}`, { 
    headers: HEADERS,
    tags: { name: 'Get User By ID' } // Agrupa métricas para evitar URLs únicas por ID
  });
  check(getSenderRes, {
    'Get User OK (200)': (r) => r.status === 200,
  });

  // --- 4. Transferência ---
  const transferPayload = JSON.stringify({
    senderAccountId: senderId, // Assumindo que ID do usuário = ID da conta
    receiverAccountId: receiverId,
    amount: randomIntBetween(1, 100),
    idempotencyKey: uuidv4(),
  });

  const transferRes = http.post(`${BASE_URL}/transfers`, transferPayload, { headers: HEADERS });

  check(transferRes, {
    'Transfer accepted (202/200)': (r) => r.status === 202 || r.status === 200,
  });

  // sleep(1); // Removido para estressar o banco de dados ao máximo
}