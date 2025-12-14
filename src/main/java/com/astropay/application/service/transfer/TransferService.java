package com.astropay.application.service.transfer;

import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transfer.Transfer;

public interface TransferService {

    /**
     * Processa uma transferência e retorna o registro da transação.
     * A transação retornada terá um status de COMPLETED ou FAILED.
     *
     * @param transfer O objeto de transferência contendo os detalhes.
     * @return A entidade Transaction com o resultado final.
     */
    Transaction transfer(Transfer transfer);
}
