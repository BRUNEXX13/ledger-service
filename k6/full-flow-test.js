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
function generateDocument(isReceiver = false) {
  // Gera um número determinístico e único para o teste
  // Base: 10 bilhões (garante 11 dígitos)
  // Offset VU: Garante que cada VU tenha sua faixa de números
  // Offset Iter * 2: Cria espaço para dois documentos por iteração
  // isReceiver: Adiciona +1 para ímpares (Receiver), 0 para pares (Sender)
  const base = 10000000000;
  const uniqueNum = base + (__VU * 1000000) + (__ITER * 2) + (isReceiver ? 1 : 0);
  return uniqueNum.toString();
}

export default function () {
  // Usa o UUID completo para garantir unicidade absoluta no e-mail/nome
  const uniqueId = uuidv4();

  // --- 1. Criação do Usuário Remetente (Sender) ---
  // Assume-se que a criação já define um saldo inicial ou cria a conta
  const senderPayload = JSON.stringify({
    name: `Sender ${uniqueId}`,
    document: generateDocument(false),
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
    document: generateDocument(true),
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