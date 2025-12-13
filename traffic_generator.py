import requests
import uuid
import random
import time
import concurrent.futures
from datetime import datetime

# Configurações
BASE_URL = "http://localhost:8082/api/v1"
HEADERS = {"Content-Type": "application/json"}

# IDs baseados no script V1__insert_initial_data.sql
ACCOUNT_IDS = [1, 2]

def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")

def make_transfer():
    """Simula uma transferência (pode ser válida, sem saldo ou inválida)"""
    sender = random.choice(ACCOUNT_IDS)
    receiver = 2 if sender == 1 else 1

    # 10% de chance de gerar erro de validação (valor negativo)
    if random.random() < 0.1:
        amount = -50.00
        scenario = "INVALID_AMOUNT"
    # 20% de chance de tentar transferir muito dinheiro (Sem saldo)
    elif random.random() < 0.3:
        amount = 50000.00
        scenario = "INSUFFICIENT_BALANCE"
    # 70% de chance de sucesso (valor normal)
    else:
        amount = round(random.uniform(10.0, 100.0), 2)
        scenario = "SUCCESS"

    payload = {
        "senderAccountId": sender,
        "receiverAccountId": receiver,
        "amount": amount,
        "idempotencyKey": str(uuid.uuid4())
    }

    try:
        response = requests.post(f"{BASE_URL}/transfers", json=payload, headers=HEADERS)
        log(f"Transfer ({scenario}): Status {response.status_code}")
    except Exception as e:
        log(f"Transfer Error: {e}")

def get_transactions():
    """Busca lista de transações (Gera carga no banco e logs)"""
    try:
        response = requests.get(f"{BASE_URL}/transactions?page=0&size=20", headers=HEADERS)
        log(f"List Transactions: Status {response.status_code}")
    except Exception as e:
        log(f"List Error: {e}")

def get_transaction_by_id():
    """Busca transação específica (Testa o Cache Redis)"""
    # Tenta buscar IDs aleatórios entre 1 e 50.
    # Se já existir, vai pro cache na segunda tentativa.
    # Se não existir, gera 404 (bom para métricas de erro).
    tx_id = random.randint(1, 50)
    try:
        response = requests.get(f"{BASE_URL}/transactions/{tx_id}", headers=HEADERS)
        hit_miss = "HIT/MISS" if response.status_code == 200 else "NOT_FOUND"
        log(f"Get Transaction {tx_id}: Status {response.status_code} ({hit_miss})")
    except Exception as e:
        log(f"Get Tx Error: {e}")

def simulate_user_behavior():
    """Função executada por cada thread"""
    while True:
        action = random.choice(['transfer', 'list', 'get_id', 'get_id']) # Mais peso para leitura (cache)

        if action == 'transfer':
            make_transfer()
        elif action == 'list':
            get_transactions()
        elif action == 'get_id':
            get_transaction_by_id()

        # Espera aleatória para variar a latência e não ser bloqueado instantaneamente pelo Rate Limit
        time.sleep(random.uniform(0.1, 1.0))

def start_load(workers=5):
    log(f"Iniciando gerador de tráfego com {workers} threads simultâneas...")
    log("Pressione CTRL+C para parar.")

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [executor.submit(simulate_user_behavior) for _ in range(workers)]
        concurrent.futures.wait(futures)

if __name__ == "__main__":
    # Verifica se o serviço está de pé antes de começar
    try:
        requests.get(f"{BASE_URL}/actuator/health")
        start_load(workers=4) # 4 usuários simultâneos
    except requests.exceptions.ConnectionError:
        print("ERRO: O serviço não parece estar rodando em localhost:8082.")
        print("Certifique-se de rodar a aplicação no IntelliJ ou Docker antes de executar este script.")
