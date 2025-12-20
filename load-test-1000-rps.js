import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

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
      duration: '5m',
      
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

// Função auxiliar para gerar IDs aleatórios (simulando usuários existentes)
// Assumindo que temos usuários com IDs de 1 a 100000 no banco
const MAX_USER_ID = 100000;

export default function () {
  // Seleciona aleatoriamente um remetente e um destinatário
  const senderId = randomIntBetween(1, MAX_USER_ID);
  let receiverId = randomIntBetween(1, MAX_USER_ID);
  
  // Garante que não estamos transferindo para a mesma conta
  while (receiverId === senderId) {
    receiverId = randomIntBetween(1, MAX_USER_ID);
  }

  const payload = JSON.stringify({
    senderId: senderId,
    receiverId: receiverId,
    amount: 10.00, // Valor fixo para simplificar
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Envia a requisição de transferência
  const res = http.post('http://localhost:8082/api/v1/transfers', payload, params);

  // Validações
  check(res, {
    'status is 202': (r) => r.status === 202,
  });
}
