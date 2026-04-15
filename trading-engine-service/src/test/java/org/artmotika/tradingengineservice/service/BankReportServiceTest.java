package org.artmotika.tradingengineservice.service;

import org.artmotika.tradingengineservice.model.Asset;
import org.artmotika.tradingengineservice.model.Order;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.model.User;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankReportServiceTest {

    @Mock private TradeLedgerRepository ledgerRepository;
    @InjectMocks private BankReportService bankReportService;

    @TempDir Path tempDir;

    @Test
    void generateDailyBankReport_ShouldCreateFileInOutbox() throws Exception {
        Path archive = tempDir.resolve("archive");
        Path outbox = tempDir.resolve("outbox");
        ReflectionTestUtils.setField(bankReportService, "reportStoragePath", archive.toString());
        ReflectionTestUtils.setField(bankReportService, "reportOutboxPath", outbox.toString());

        User user = new User(); user.setId("u1");
        Asset asset = new Asset(); asset.setId("a1");
        Order order = new Order(); order.setId("o1"); order.setUser(user); order.setAsset(asset); order.setType(Order.OrderType.BUY);
        
        TradeLedger ledger = new TradeLedger();
        ledger.setId("l1");
        ledger.setOrder(order);
        ledger.setTransactionHash("tx1");
        ledger.setExecutionPrice(new BigDecimal("100"));
        ledger.setTimestamp(LocalDateTime.now());

        when(ledgerRepository.findByTimestampAfter(any())).thenReturn(List.of(ledger));

        bankReportService.generateDailyBankReport();

        // Check if file exists in archive and outbox
        assertTrue(Files.list(archive).findAny().isPresent());
        assertTrue(Files.list(outbox).findAny().isPresent());
    }

    @Test
    void generateDailyBankReport_ShouldSkipIfNoTrades() throws Exception {
        Path outbox = tempDir.resolve("outbox");
        ReflectionTestUtils.setField(bankReportService, "reportOutboxPath", outbox.toString());
        when(ledgerRepository.findByTimestampAfter(any())).thenReturn(Collections.emptyList());

        bankReportService.generateDailyBankReport();

        verify(ledgerRepository, times(1)).findByTimestampAfter(any());
        assertTrue(!Files.exists(outbox) || Files.list(outbox).count() == 0);
    }
}
