package org.artmotika.tradingengineservice.service;

import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankReportServiceTest {

    @Mock private TradeLedgerRepository ledgerRepository;
    @InjectMocks private BankReportService bankReportService;

    @Test
    void generateDailyBankReport_ShouldCreateFileAndOutboxCopy() throws IOException {
        Path tempDir = Files.createTempDirectory("reports");
        ReflectionTestUtils.setField(bankReportService, "reportStoragePath", tempDir.resolve("archive").toString());
        ReflectionTestUtils.setField(bankReportService, "reportOutboxPath", tempDir.resolve("outbox").toString());

        Asset asset = new Asset(); asset.setId("a1");
        
        Order order = new Order();
        order.setUserId("u1");
        order.setAsset(asset);
        order.setType(Order.OrderType.BUY);

        TradeLedger trade = new TradeLedger();
        trade.setId("t1");
        trade.setOrder(order);
        trade.setExecutionPrice(BigDecimal.TEN);
        trade.setTimestamp(LocalDateTime.now());
        trade.setTransactionHash("hash123");

        when(ledgerRepository.findByTimestampAfter(any())).thenReturn(List.of(trade));

        bankReportService.generateDailyBankReport();

        // Check if report exists in outbox
        Path outbox = tempDir.resolve("outbox");
        assertTrue(Files.list(outbox).findAny().isPresent());
    }
}
