package com.payline.payment.template.test.integration;

import com.payline.payment.template.services.PaymentServiceImpl;
import com.payline.payment.template.services.PaymentWithRedirectionServiceImpl;
import com.payline.payment.template.services.RefundServiceImpl;
import com.payline.payment.template.test.Utils;
import com.payline.payment.template.utils.TemplateCardConstants;
import com.payline.pmapi.bean.payment.ContractConfiguration;
import com.payline.pmapi.bean.payment.ContractProperty;
import com.payline.pmapi.bean.payment.Environment;
import com.payline.pmapi.bean.payment.request.PaymentRequest;
import com.payline.pmapi.bean.payment.request.RedirectionPaymentRequest;
import com.payline.pmapi.bean.payment.response.PaymentResponse;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseRedirect;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseSuccess;
import com.payline.pmapi.bean.refund.request.RefundRequest;
import com.payline.pmapi.bean.refund.response.RefundResponse;
import com.payline.pmapi.bean.refund.response.impl.RefundResponseSuccess;
import com.payline.pmapi.service.PaymentService;
import com.payline.pmapi.service.PaymentWithRedirectionService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.payline.payment.template.test.Utils.SUCCESS_URL;
import static com.payline.pmapi.integration.AbstractPaymentIntegration.CANCEL_URL;

public class ItRefundTest {
    private PaymentServiceImpl paymentService = new PaymentServiceImpl();
    private PaymentWithRedirectionServiceImpl paymentWithRedirectionService = new PaymentWithRedirectionServiceImpl();
    private RefundServiceImpl refundService = new RefundServiceImpl();

    private Map<String, ContractProperty> generateParameterContract() {
        Map<String, ContractProperty> propertyHashMap = new HashMap<>();
        propertyHashMap.put(TemplateCardConstants.AUTHORISATIONKEY_KEY, new ContractProperty(Utils.AUTHORISATION_VAL));

        return propertyHashMap;
    }

    private String payOnPartnerWebsite(String partnerUrl) {
        // Start browser
        WebDriver driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        try {
            // Go to partner's website
            driver.get(partnerUrl);
            driver.findElement(By.id("classicPin-addPinField")).sendKeys(Utils.PAYMENT_TOKEN);

            // accept CGU
            driver.findElement(By.xpath("/html/body/section[1]/section[1]/div[1]/div/div/article/div[1]/div[4]/div[1]/div/div[1]/label")).click();

            // validate payment
            driver.findElement(By.xpath("//*[@id='payBtn']")).click();

            // Wait for redirection to success or cancel url
            WebDriverWait wait = new WebDriverWait(driver, 30);
            wait.until(ExpectedConditions.or(ExpectedConditions.urlToBe(SUCCESS_URL), ExpectedConditions.urlToBe(CANCEL_URL)));
            return driver.getCurrentUrl();
        } finally {
            driver.quit();
        }
    }


    @Test
    public void fullRefundTest() {
        PaymentRequest request = Utils.createCompletePaymentBuilder().build();
        String partnerTransactionId = fullRedirectionPayment(request, paymentService, paymentWithRedirectionService);

        // create the refund request from previous request
        RefundRequest refund = RefundRequest.RefundRequestBuilder.aRefundRequest()
                .withAmount(request.getAmount())
                .withOrder(request.getOrder())
                .withBuyer(request.getBuyer())
                .withContractConfiguration(request.getContractConfiguration())
                .withEnvironment(request.getEnvironment())
                .withTransactionId(request.getTransactionId())
                .withPartnerTransactionId(partnerTransactionId)
                .withSoftDescriptor(request.getSoftDescriptor())
                .withPartnerConfiguration(request.getPartnerConfiguration())
                .build();

        RefundResponse refundResponse = refundService.refundRequest(refund);
        Assert.assertEquals(RefundResponseSuccess.class, refundResponse.getClass());
    }

    private String fullRedirectionPayment(PaymentRequest paymentRequest, PaymentService paymentService, PaymentWithRedirectionService paymentWithRedirectionService) {
        PaymentResponse paymentResponseFromPaymentRequest = paymentService.paymentRequest(paymentRequest);
        PaymentResponseRedirect paymentResponseRedirect = (PaymentResponseRedirect) paymentResponseFromPaymentRequest;
        String partnerUrl = paymentResponseRedirect.getRedirectionRequest().getUrl().toString();
        String redirectionUrl = this.payOnPartnerWebsite(partnerUrl);
        Assertions.assertEquals("https://succesurl.com/", redirectionUrl);
        String partnerTransactionId = paymentResponseRedirect.getPartnerTransactionId();
        PaymentResponse paymentResponseFromFinalize = this.handlePartnerResponse(paymentWithRedirectionService, paymentRequest, paymentResponseRedirect);
        PaymentResponseSuccess paymentResponseSuccess = (PaymentResponseSuccess) paymentResponseFromFinalize;
        Assertions.assertNotNull(paymentResponseSuccess.getTransactionDetails());
        Assertions.assertEquals(partnerTransactionId, paymentResponseSuccess.getPartnerTransactionId());

        return partnerTransactionId;
    }

    private PaymentResponse handlePartnerResponse(PaymentWithRedirectionService paymentWithRedirectionService, PaymentRequest paymentRequest, PaymentResponseRedirect paymentResponseRedirect) {
        ContractConfiguration contractConfiguration = new ContractConfiguration("", this.generateParameterContract());
        Environment Environment = new Environment("http://google.com/", SUCCESS_URL, CANCEL_URL, true);
        RedirectionPaymentRequest redirectionPaymentRequest = RedirectionPaymentRequest.builder().withContractConfiguration(contractConfiguration).withPaymentFormContext(null).withEnvironment(Environment).withTransactionId(paymentRequest.getTransactionId()).withRequestContext(paymentResponseRedirect.getRequestContext()).withAmount(paymentRequest.getAmount()).build();
        return paymentWithRedirectionService.finalizeRedirectionPayment(redirectionPaymentRequest);
    }

}
