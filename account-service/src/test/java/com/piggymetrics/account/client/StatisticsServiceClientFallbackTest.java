package com.piggymetrics.account.client;

import com.piggymetrics.account.domain.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Verifies the Resilience4j-backed Feign fallback logs an error when the
 * downstream statistics-service is unavailable.
 *
 * @author cdov
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "spring.cloud.openfeign.circuitbreaker.enabled=true"
})
public class StatisticsServiceClientFallbackTest {

    @Autowired
    private StatisticsServiceClient statisticsServiceClient;

    @Test
    public void testUpdateStatisticsWithFailFallback(CapturedOutput output) {
        statisticsServiceClient.updateStatistics("test", new Account());
        assertThat(output.toString(), containsString("Error during update statistics for account: test"));
    }
}
